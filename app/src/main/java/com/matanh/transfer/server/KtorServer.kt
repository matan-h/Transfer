package com.matanh.transfer.server

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.matanh.transfer.util.FileUtils
import com.matanh.transfer.R
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.http.content.CompressedFileType
import io.ktor.server.http.content.resolveResource
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.jvm.javaio.toOutputStream
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.channels.Channels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val TAG_KTOR_MODULE = "TransferKtorModule"
private val logger = Timber.tag(TAG_KTOR_MODULE)

// --- Custom Plugins (CurlDetectorPlugin, IpAddressApprovalPlugin) ---
private val IsCurlRequestKey = AttributeKey<Boolean>("IsCurlRequestKey")

val CurlDetectorPlugin = createApplicationPlugin(name = "CurlDetectorPlugin") {
    onCall { call ->
        val userAgent = call.request.headers[HttpHeaders.UserAgent]
        if (userAgent != null && userAgent.contains("curl", ignoreCase = true)) {
            call.attributes.put(IsCurlRequestKey, true)
        }
    }
}

val IpAddressApprovalPlugin = createApplicationPlugin(name = "IpAddressApprovalPlugin") {
    val serviceProvider = application.attributes[KEY_SERVICE_PROVIDER]
    onCall { call ->
        val service = serviceProvider() ?: run {
            logger.e("FileServerService not available to IPAddressApprovalPlugin")
            call.respond(HttpStatusCode.InternalServerError, "Server configuration error.")
            return@onCall
        }
        val clientIp = call.request.origin.remoteHost
        logger.d("IP Approval: Checking IP $clientIp")
        if (service.isIpPermissionRequired()) {
            val approved = service.requestIpApprovalFromClient(clientIp)
            if (!approved) {
                logger.w("IP Approval: IP $clientIp denied access.")
                call.respond(HttpStatusCode.Forbidden, "Access denied by host device.")
                return@onCall
            } else {
                logger.d("IP Approval: IP $clientIp approved.")
            }
        }
    }
}
private val KEY_SERVICE_PROVIDER = AttributeKey<() -> FileServerService?>("ServiceProviderKey")

// --- Shared File Handling Functions ---
suspend fun handleFileDownload(
    call: RoutingCall,
    context: Context,
    baseDocumentFile: DocumentFile,
    fileName: String
) {
    // 1. URL Decode filename - done automatically by the browser
    // 2. Locate & validate
    val target = baseDocumentFile.findFile(fileName)
    if (target == null || !target.isFile || !target.canRead()) {
        return call.respond(HttpStatusCode.NotFound, "File not found: $fileName")
    }
    // 3. Determine mime & optional length
    val mime = ContentType.parse(target.type ?: ContentType.Application.OctetStream.toString())
    val length = target.length().takeIf { it > 0L }

    // 4. Open the Android stream once
    val inputStream = context.contentResolver.openInputStream(target.uri)
        ?: return call.respond(HttpStatusCode.InternalServerError, "Could not open file stream.")

    // 5. Respond with full control over headers & streaming - should be fast.
    try {
        call.respond(object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType = mime
            override val contentLength: Long? = length
            override val headers: Headers = headersOf(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment
                    .withParameter(ContentDisposition.Parameters.FileName, fileName)
                    .toString()
            )

            override suspend fun writeTo(channel: ByteWriteChannel) {
                // Stream in a single IO-optimized coroutine
                withContext(Dispatchers.IO) {
                    inputStream.use { stream ->
                        val buffer = ByteArray(256 * 1024) // 256 KB chunks
                        while (true) {
                            val bytesRead = stream.read(buffer)
                            if (bytesRead == -1) break
                            channel.writeFully(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        })
    } catch (e: Exception) {
        logger.e("Error streaming file $fileName")
        call.respond(
            HttpStatusCode.InternalServerError,
            "Error serving file: ${e.localizedMessage}"
        )
    } finally {
        inputStream.close()
    }
}


suspend fun handleFileUpload(
    context: Context,
    baseDocumentFile: DocumentFile,
    originalFileName: String,
    byteReadChannelProvider: suspend () -> ByteReadChannel,
    notifyService: () -> Unit
): Pair<String?, String?> {

    // 1. Sanitize and ensure unique filename
    val sanitizedFileName = originalFileName.replace(Regex("""(^\\s+|\\s+\$|^\\.\\.|[\\/])"""), "_")
    /*
    ^\s+ - Leading whitespace
    \s+$ - Trailing whitespace
    ^\.\. - ".." at start
    [\\/] - Path separators
     */

    // 2. Generate a unique filename
    val nameWithoutExt = sanitizedFileName.substringBeforeLast('.', sanitizedFileName)
    val extension = sanitizedFileName.substringAfterLast('.', "")
    val uniqueFileName =
        FileUtils.generateUniqueFileName(baseDocumentFile, nameWithoutExt, extension)

    // Always create with no specific mime (prevent the provider from adding a file extension)
    val effectiveMimeType = ContentType.Application.OctetStream.toString()
    val newFileDoc = baseDocumentFile.createFile(effectiveMimeType, uniqueFileName)
    if (newFileDoc == null || !newFileDoc.canWrite()) {
        logger.e("Failed to create document file for upload: $uniqueFileName")
        return null to "Failed to create file."
    }
    // 5) Stream upload with a buffer
    try {
        val byteReadChannel = byteReadChannelProvider()

        context.contentResolver.openOutputStream(newFileDoc.uri)?.use { outputStream ->
            val channel = Channels.newChannel(outputStream)
            byteReadChannel.copyTo(channel)
        } ?: throw Exception("Cannot open output stream for ${newFileDoc.uri}")

        logger.i("File '$uniqueFileName' uploaded successfully.")
        notifyService()
        return uniqueFileName to null
    } catch (e: Exception) {
        newFileDoc.delete() // Clean up
        logger.e("Error during file upload: $uniqueFileName")
        return null to e.localizedMessage
    }
}

fun handleFileDelete(
    baseDocumentFile: DocumentFile,
    fileName: String,
    notifyService: () -> Unit
): Pair<Boolean, String?> {

    val fileToDeleteDoc = baseDocumentFile.findFile(fileName)
    if (fileToDeleteDoc == null || !fileToDeleteDoc.exists()) {
        return false to "File not found: $fileName"
    }
    return if (fileToDeleteDoc.delete()) {
        logger.i("File deleted successfully: $fileName")
        notifyService()
        true to null
    } else {
        logger.e("Failed to delete file: $fileName")
        false to "Failed to delete file: $fileName"
    }
}
suspend fun handleZipDownload(
    call: RoutingCall,
    context: Context,
    baseDocumentFile: DocumentFile,
    requestedFileNames: List<String>?
) {
    val allFiles = baseDocumentFile.listFiles().filter { it.isFile && it.canRead() }

    // Logic: If requestedFileNames is null or empty, take everything.
    // Otherwise, filter allFiles to match the names in requestedFileNames.
    val filesToZip = if (requestedFileNames.isNullOrEmpty()) {
        allFiles
    } else {
        allFiles.filter { it.name in requestedFileNames }
    }

    if (filesToZip.isEmpty()) {
        return call.respondText(
            text = "No files found to zip",
            status = HttpStatusCode.UnprocessableEntity
        )

    }

    val zipFileName = "transfer_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.zip"

    try {
        call.respond(object : OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType = ContentType.Application.Zip
            override val headers: Headers = headersOf(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    zipFileName
                ).toString()
            )

            override suspend fun writeTo(channel: ByteWriteChannel) {
                withContext(Dispatchers.IO) {
                    val outputStream: OutputStream = channel.toOutputStream()
                    ZipOutputStream(outputStream).use { zipOut ->
                        val buffer = ByteArray(256 * 1024)
                        for (file in filesToZip) {
                            val name = file.name ?: continue
                            zipOut.putNextEntry(ZipEntry(name))
                            context.contentResolver.openInputStream(file.uri)?.use { input ->
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    zipOut.write(buffer, 0, read)
                                }
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        })
    } catch (e: Exception) {
        logger.e(e, "Error zipping files")
        if (!call.response.isCommitted) {
            call.respond(HttpStatusCode.InternalServerError, "Zip Error: ${e.localizedMessage}")
        }
    }
}

// --- Ktor Application Module ---
fun Application.ktorServer(
    context: Context,
    serviceProviderLambda: () -> FileServerService?,
    sharedDirUri: Uri
) {
    val applicationContext = context
    attributes.put(KEY_SERVICE_PROVIDER, serviceProviderLambda)
    val fileServerService = serviceProviderLambda()
    if (fileServerService == null) {
        log.error("FileServerService is null in Ktor module. Server might not function correctly.")
        return
    }
    val baseDocumentFile = DocumentFile.fromTreeUri(applicationContext, sharedDirUri)
    if (baseDocumentFile == null || !baseDocumentFile.isDirectory || !baseDocumentFile.canRead()) {
        log.error("Shared directory URI is not accessible: $sharedDirUri")
        return
    }

    // Install Plugins
    install(CurlDetectorPlugin)
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.e("Unhandled error: ${cause.localizedMessage}")
            call.respondText(
                text = "500: ${cause.localizedMessage}",
                status = HttpStatusCode.InternalServerError
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Page Not Found", status = status)
        }
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-File-Name")
        anyHost()
        allowCredentials = true
    }
    install(Authentication) {
        basic("auth-basic") {
            realm = applicationContext.getString(R.string.app_name)
            validate { credentials ->
                if (fileServerService.isPasswordProtectionEnabled()) {
                    if (fileServerService.checkPassword(credentials.password)) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                } else {
                    UserIdPrincipal(credentials.name)
                }
            }
        }
    }
    install(IpAddressApprovalPlugin)
    install(ContentNegotiation) { json() }

    // Routing
    routing {
        staticResources("/assets", "assets") {
            preCompressed(CompressedFileType.GZIP)
            default("index.html")
        }

        authenticate("auth-basic", optional = !fileServerService.isPasswordProtectionEnabled()) {
            get("/") {
                val isCurl = call.attributes.getOrNull(IsCurlRequestKey) == true
                if (isCurl) {
                    val fileNames = baseDocumentFile.listFiles()
                        .filter { it.isFile }
                        .joinToString("\n") { it.name ?: "unknown_file" }
                    call.respondText(fileNames, ContentType.Text.Plain)
                }
                val resource = call.resolveResource("index.html", "assets")
                if (resource != null) {
                    call.respond(resource)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        "UI not found (index.html missing in assets)."
                    )
                }
            }

            route("/api") {
                get("/ping") {
                    call.respondText("pong")
                }

                get("/files") {
                    try {
                        val filesList = baseDocumentFile.listFiles()
                            .filter { it.isFile && it.canRead() }
                            .mapNotNull { docFile ->
                                val lastModifiedDate = Date(docFile.lastModified())
                                val dateFormat = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss",
                                    Locale.getDefault()
                                ).apply {
                                    timeZone = TimeZone.getDefault()
                                }
                                FileInfo(
                                    name = docFile.name ?: "Unknown",
                                    size = docFile.length(),
                                    formattedSize = FileUtils.formatFileSize(docFile.length()),
                                    lastModified = dateFormat.format(lastModifiedDate),
                                    type = docFile.type ?: "unknown",
                                    downloadUrl = "/api/download/${
                                        URLEncoder.encode(
                                            docFile.name,
                                            "UTF-8"
                                        ).replace("+", "%20")
                                    }" // URL-encode the filename using the correct way for path segments.
                                )
                            }
                        logger.d("Files list: $filesList")
                        call.respond(FileListResponse(filesList))
                    } catch (e: Exception) {
                        logger.e("Error listing files")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Error listing files: ${e.localizedMessage}")
                        )
                    }
                }

                get("/download/{fileNameEncoded}") {
                    val fileNameEncoded = call.parameters["fileNameEncoded"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "File name missing.")
                        return@get
                    }
                    handleFileDownload(call, applicationContext, baseDocumentFile, fileNameEncoded)
                }
                get("/zip") {
                    handleZipDownload(call, applicationContext, baseDocumentFile,null)
                }
                post("/zip"){
                    val files = call.receiveParameters().getAll("f")

                    handleZipDownload(call, applicationContext, baseDocumentFile, files)

                }


                post("/upload") {
                    var filesUploadedCount = 0
                    val uploadedFileNames = mutableListOf<String>()
                    try {
                        val multipart = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE) // allow more then 50MB (#3)
                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FileItem -> {
                                    val originalFileName = part.originalFileName ?: "uploaded_file"
                                    logger.d("Receiving file: $originalFileName")
                                    val (fileName, error) = handleFileUpload(
                                        context = applicationContext,
                                        baseDocumentFile = baseDocumentFile,
                                        originalFileName = originalFileName,
                                        byteReadChannelProvider = { part.provider() },
                                        notifyService = { fileServerService.notifyFilePushed() }
                                    )
                                    if (fileName != null) {
                                        uploadedFileNames.add(fileName)
                                        filesUploadedCount++
                                    } else {
                                        logger.e("Upload failed for $originalFileName: $error")
                                    }
                                }

                                is PartData.FormItem -> {
                                    logger.d("Form item: ${part.name} = ${part.value}")
                                }

                                else -> {}
                            }
                            part.dispose()
                        }
                        if (filesUploadedCount > 0) {
                            call.respondText(
                                "Successfully uploaded: ${
                                    uploadedFileNames.joinToString(
                                        ", "
                                    )
                                }"
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                "No files were uploaded or upload failed."
                            )
                        }
                    } catch (e: Exception) {
                        logger.e("Exception during file upload")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Upload error: ${e.localizedMessage}"
                        )
                    }
                }

                post("/delete") {
                    try {
                        val requestBody = call.receiveText()
                        val jsonObject = JSONObject(requestBody)
                        val fileNameToDelete = jsonObject.optString("filename", "")
                        if (fileNameToDelete.isEmpty()) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("Filename not provided.")
                            )
                            return@post
                        }
                        val (success, error) = handleFileDelete(
                            baseDocumentFile = baseDocumentFile,
                            fileName = fileNameToDelete,
                            notifyService = { fileServerService.notifyFilePushed() }
                        )
                        if (success) {
                            call.respond(
                                HttpStatusCode.OK,
                                SuccessResponse("File '$fileNameToDelete' deleted.")
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse(error ?: "Failed to delete file.")
                            )
                        }
                    } catch (e: Exception) {
                        logger.e(e,"Error processing delete request")
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Server error during delete: ${e.localizedMessage}")
                        )
                    }
                }
            }

            // HTTP Interface
            put("/{fileName}") {
                val fileName = call.parameters["fileName"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Filename missing in path for PUT.")
                    return@put
                }
                // handle simple override
                val targetFileDoc = baseDocumentFile.findFile(fileName)
                if (targetFileDoc != null && targetFileDoc.exists()) {
                    targetFileDoc.delete()
                }

                val (uploadedFileName, error) = handleFileUpload(
                    context = applicationContext,
                    baseDocumentFile = baseDocumentFile,
                    originalFileName = fileName,
                    byteReadChannelProvider = { call.receiveChannel() },
                    notifyService = { fileServerService.notifyFilePushed() }
                )
                if (uploadedFileName != null) {
                    call.respond(
                        HttpStatusCode.Created,
                        "File '$uploadedFileName' uploaded via PUT."
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Error during PUT upload: $error"
                    )
                }
            }

            get("/{fileName}") {
                val fileNameEncoded = call.parameters["fileName"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Filename missing in path.")
                    return@get
                }
                handleFileDownload(call, applicationContext, baseDocumentFile, fileNameEncoded)
            }

            delete("/{fileName}") {
                val fileName = call.parameters["fileName"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Filename missing in path.")
                    return@delete
                }
                val (success, error) = handleFileDelete(
                    baseDocumentFile = baseDocumentFile,
                    fileName = fileName,
                    notifyService = { fileServerService.notifyFilePushed() }
                )
                if (success) {
                    call.respondText("File '$fileName' deleted.", status = HttpStatusCode.OK)
                } else {
                    call.respondText(
                        "Error: ${error ?: "Could not delete file '$fileName'."}",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}

// --- Serializable Data Classes ---
@Serializable
data class FileInfo(
    val name: String,
    val size: Long,
    val formattedSize: String,
    val lastModified: String,
    val type: String,
    val downloadUrl: String
)

@Serializable
data class FileListResponse(val files: List<FileInfo>)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class SuccessResponse(val message: String)