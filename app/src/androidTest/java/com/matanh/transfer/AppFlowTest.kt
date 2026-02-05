package com.matanh.transfer

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.*
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.FileAdapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.awaitility.kotlin.await
import org.junit.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// --- Helpers to keep JSON access clean ---
fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

fun JsonObject.long(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull

fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.jsonObject

fun JsonObject.array(key: String): JsonArray? =
    this[key]?.jsonArray


fun String.encodeURL(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

fun String.decodeURL(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8.name())


/**
 * Full end-to-end integration test for the Transfer app.
 * This test covers the following flow:
 * 1.  Launches the app and handles the initial `SetupActivity`.
 * 2.  Uses UI Automator to interact with the system folder picker.
 * 3.  It checks if a "Storage" folder exists. If not, it creates it using the picker's UI.
 * 4.  It selects the "Storage" folder.
 * 5.  Waits for `MainActivity` to launch and the `FileServerService` to start.
 * 6.  Verifies the server status and IP address are correctly displayed.
 * 7.  Uses an HTTP client (OkHttp) to upload a file to the running server.
 * 8.  Verifies the uploaded file appears in the `RecyclerView`.
 * 9.  Uses Espresso to delete the file via the UI.
 * 11. Uses the HTTP client to confirm the file is also deleted from the server.
 *
 * NOTE: This test requires API level 29+ to reliably interact with the scoped storage picker UI.
 * It is also recommended to run this on an emulator with network access enabled.
 * This test will create a "Storage" folder in the root of the device's internal storage if it doesn't exist.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppFlowTest {

    // Rule to launch the initial activity of the app.
    @get:Rule
    val activityRule = ActivityScenarioRule(SetupActivity::class.java)

    // Rule to grant necessary permissions before the test runs.
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private lateinit var device: UiDevice
    private val testFolderName = "Storage"
    private val testFileName = "test_upload.txt"
    private val testFileContent = "This is a file for integration testing."

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        private const val UI_AUTOMATOR_TIMEOUT = 5000L
        private val createdFiles: MutableSet<String> = mutableSetOf()

        private var serverUrl: String? = null

        // Clear shared preferences before the test suite runs to ensure a clean state
        @BeforeClass
        @JvmStatic
        fun setupClass() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs =
                context.getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            if (serverUrl == null) return

// Try to delete every created file. Ignore failures but log them.
            createdFiles.forEach { filename ->
                try {
                    val encoded = filename.encodeURL()

                    val deleteReq1 = Request.Builder()
                        .url("$serverUrl/$encoded")
                        .delete()
                        .build();
                    client.newCall(deleteReq1).execute().close()

                } catch (e: Exception) {
                    Log.e(
                        "Test",
                        "Failed to delete test file '$filename' during cleanup: ${e.message}"
                    );
                }
            }
        }
    }

    @Before
    fun setUp() {
        // Initialize UI Automator
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
    }

    private fun addToCreated(filename: String) {
        synchronized(createdFiles) { createdFiles.add(filename) }
    }

    private fun uploadFileHttp(
        encodedFilename: String,
        content: String,
        mimetype: String = "text/plain"
    ): Boolean {
        val requestBody = content.toRequestBody(mimetype.toMediaType())
        val request = Request.Builder().url("$serverUrl/$encodedFilename").put(requestBody).build()
        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                addToCreated(encodedFilename)
                return true
            }
            return false
        }
    }

    private fun checkFileContent(
        filenameEncoded: String,
        expectedContent: String? = null
    ): Boolean {
        val request =
            Request.Builder().url("$serverUrl/api/download/$filenameEncoded").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return false
            if (expectedContent == null) return true
            val body = resp.body?.string() ?: return false
            return body == expectedContent
        }
    }

    private fun getFilesJson(): JsonObject? {
        val request = Request.Builder().url("$serverUrl/api/files").get().build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null;
            val body = resp.body?.string() ?: return null
            return json.parseToJsonElement(body).jsonObject
        }
    }


    @Test
    fun testA_initialSetupAndFolderSelection() {
        // --- Step 1: Handle SetupActivity and Folder Picker ---
        // The app starts on SetupActivity, we need to select a folder.
        onView(withId(R.id.btnChooseFolder)).check(matches(isDisplayed()))
        onView(withId(R.id.btnChooseFolder)).perform(click())

        // --- UI Automator part to handle the system folder picker ---
        // Wait for the folder picker to appear.
        device.wait(Until.hasObject(By.textContains("Choose a folder")), UI_AUTOMATOR_TIMEOUT)

        // Check if the "Storage" folder already exists.
        var storageFolder = device.findObject(By.text(testFolderName))

        if (storageFolder == null) {
            // If it doesn't exist, create it.
            // The "Create new folder" button can have different descriptions or resource IDs.
            val createFolderButton = device.findObject(
                By.clazz("android.widget.Button").text("CREATE NEW FOLDER")
            )
            Assert.assertNotNull("Could not find 'CREATE NEW FOLDER' button.", createFolderButton)
            createFolderButton.click()

            // Wait for the dialog and enter the folder name.
            val editText = device.wait(
                Until.findObject(By.clazz("android.widget.EditText")),
                UI_AUTOMATOR_TIMEOUT
            )
            Assert.assertNotNull("Could not find EditText for new folder name.", editText)
            editText.text = testFolderName

            // Click OK
            val okButton = device.findObject(By.text("OK"))
            okButton.click()

            // Wait for the folder to appear in the list and re-assign the variable
            storageFolder =
                device.wait(Until.findObject(By.text(testFolderName)), UI_AUTOMATOR_TIMEOUT)
            Assert.assertNotNull("Newly created '$testFolderName' folder not found.", storageFolder)
        }

        // Now, select the folder (either pre-existing or newly created).
        storageFolder.click()
        Thread.sleep(1000) // Short delay for UI to update.

        // Click "USE THIS FOLDER" button.
        val useFolderButton = device.findObject(By.text("USE THIS FOLDER"))
        Assert.assertNotNull("USE THIS FOLDER button not found", useFolderButton)
        useFolderButton.click()

        // Handle the permission confirmation dialog.
        val allowButton = device.wait(Until.findObject(By.text("ALLOW")), UI_AUTOMATOR_TIMEOUT)
        Assert.assertNotNull("ALLOW button not found", allowButton)
        allowButton.click()


        // --- Step 2: Verify MainActivity is launched and Server Starts ---
        // After folder selection, MainActivity should be in the foreground.
        // We need to wait for the server to start. This can take a few seconds.
        await.atMost(5, TimeUnit.SECONDS).untilAsserted {
            onView(withId(R.id.tvServerStatus))
                .check(matches(withText(R.string.server_running)))
        }
//        Assert.assertTrue("Server did not start within the timeout period.", isServerRunning)

        // Verify the IP address is displayed correctly.
        var ipText = ""
        onView(withId(R.id.actvIps)).perform(object : ViewAction {
            override fun getConstraints() = isAssignableFrom(AutoCompleteTextView::class.java)
            override fun getDescription() = "Extract text from tvIpAddress"
            override fun perform(uiController: UiController, view: View) {
                ipText = (view as AutoCompleteTextView).text.toString()
            }
        })

        // sanity-check & build serverUrl
        Assert.assertTrue(
            "IP text didn’t match expected pattern",
            Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+:${Constants.SERVER_PORT}")
                .matcher(ipText)
                .matches()
        )
        serverUrl = "http://$ipText"
        Assert.assertNotNull("Failed to build serverUrl", serverUrl)

    }

    @Test
    fun testB_fileUploadAndVerification() {
        Assert.assertNotNull(
            "Server URL not set, cannot run upload test. Ensure testA passes first.",
            serverUrl
        )
        // step 2.5: allow access
        val allow_request = Request.Builder()
            .url("$serverUrl")
            .get()
            .build()

        client.newCall(allow_request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // it'll almost certainly timeout—ignore
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })


        val allowButton = device.wait(Until.findObject(By.text("ALLOW")), UI_AUTOMATOR_TIMEOUT)
        Assert.assertNotNull("ALLOW button not found", allowButton)
        allowButton.click()


        // --- Step 3: Upload a file via HTTP ---
        uploadFileHttp(testFileName, testFileContent, "text/plain")


        // --- Step 4: Verify the file appears in the RecyclerView ---
        // The service should trigger a refresh. We wait for the item to appear.
        var isFileVisible = false
        for (i in 0..5) { // Wait up to 5 seconds
            try {
                onView(withId(R.id.rvFiles))
                    .perform(
                        RecyclerViewActions.scrollTo<FileAdapter.ViewHolder>(
                            hasDescendant(
                                withText(testFileName)
                            )
                        )
                    )
                onView(withText(testFileName)).check(matches(isDisplayed()))
                isFileVisible = true
                break
            } catch (e: Exception) {
                Thread.sleep(1000)
            }
        }
        Assert.assertTrue("Uploaded file did not appear in the UI.", isFileVisible)
        Assert.assertTrue(
            "file content is not as expected",
            checkFileContent(testFileName, testFileContent)
        )
    }

    @Test
    fun testC_fileDeletionAndVerification() {
        assumeTrue("Server URL not set – did testA fail?", serverUrl != null)

        // --- Step 5: Delete the file using the UI ---
        // Long‑press the item to enter contextual action mode
        onView(withId(R.id.rvFiles))
            .perform(
                RecyclerViewActions.actionOnItem<FileAdapter.ViewHolder>(
                    hasDescendant(withText(testFileName)),
                    longClick()
                )
            )

        // Click the delete icon in the action bar
        onView(withId(R.id.action_delete_contextual)).perform(click())

        // Confirm deletion in the dialog
        onView(withText(R.string.delete)).perform(click())

        // --- Step 6: Verify the file is deleted on the server (expects 404) ---
        val deleteCheckRequest = Request.Builder()
            .url("$serverUrl/api/download/$testFileName")
            .get()
            .build()

        client.newCall(deleteCheckRequest).execute().use { resp ->
            Assert.assertEquals(
                "Expected 404 after deletion, but got ${resp.code}",
                404,
                resp.code
            )
        }
        createdFiles.remove(testFileName) // dont try to delete this file after the tests
    }

    @Test
    fun testD_FilenameEncoding() {
        assumeTrue("Server URL not set – did testA fail?", serverUrl != null)

        val filename1 = "a+b c.py"
        val filename2 = "a b+c.py"
        val content1 = "+"
        val content2 = "space"
        uploadFileHttp(filename1.encodeURL(), content1)
        uploadFileHttp(filename2.encodeURL(), content2)

        Assert.assertTrue(checkFileContent(filename1.encodeURL(), content1));
        Assert.assertTrue(checkFileContent(filename2.encodeURL(), content2));

        val jsonObj = getFilesJson()
        val files = jsonObj?.array("files") ?: return
        val file1 = files.map { it.jsonObject }.firstOrNull { it.string("name") == filename1 }
        val file2 = files.map { it.jsonObject }.firstOrNull { it.string("name") == filename2 }
        Assert.assertNotNull("File 'a+b c.py' not found in JSON", file1)
        Assert.assertNotNull("File 'a b+c.py' not found in JSON", file2)
        Assert.assertTrue(
            "Wrong encoding for 'a+b c.py': ${file1?.string("downloadUrl")}",
            file1?.string("downloadUrl")?.endsWith("a%2Bb%20c.py") == true
        )
        Assert.assertTrue(
            "Wrong encoding for 'a b+c.py': ${file2?.string("downloadUrl")}",
            file2?.string("downloadUrl")?.endsWith("a%20b%2Bc.py") == true
        )
    }

    @Test
    fun testE_largeFiles() {
        assumeTrue("Server URL not set – did testA fail?", serverUrl != null)

        val largeFileName = "large_test_1gb.bin"
        val largeFileSize: Long = 1024L * 1024L * 1024L // 1 GB
        // IMPORTANT: This may take several seconds and requires sufficient storage space on the device/emulator.
        val tempFile = createLargeFile(largeFileName, largeFileSize)
//        addToCreated(largeFileName) // Ensure it's cleaned up in tearDownClass
        // --- Step 1: Upload the large file via HTTP ---
        val requestBody = RequestBody.create(
            "application/octet-stream".toMediaType(),
            tempFile
        )

        val encodedFilename = largeFileName.encodeURL()
        val request = Request.Builder()
            .url("$serverUrl/$encodedFilename")
            .put(requestBody)
            .build()


        Log.i(
            "LargeUploadTest", "Uploading $largeFileName (${largeFileSize} bytes)"
        )
        val startTime = System.currentTimeMillis()


        client.newCall(request).execute().use { resp ->
            val duration = System.currentTimeMillis() - startTime

            Assert.assertTrue(
                "Large file upload failed. HTTP code: ${resp.code}",
                resp.isSuccessful
            )
            addToCreated(largeFileName)
            Log.i("LargeUploadTest", "Upload of $largeFileName took $duration s.")

        }
        // --- Step 2: Verify the file appears in the RecyclerView ---
        await.atMost(10, TimeUnit.SECONDS).ignoreExceptions().untilAsserted {
            onView(withId(R.id.rvFiles))
                .perform(
                    RecyclerViewActions.scrollTo<FileAdapter.ViewHolder>(
                        hasDescendant(withText(largeFileName))
                    )
                )
            onView(withText(largeFileName)).check(matches(isDisplayed()))
        }
        val filesJson = getFilesJson()
        val files = filesJson?.array("files")
        val largeFileEntry =
            files?.map { it.jsonObject }?.firstOrNull { it.string("name") == largeFileName }
        Assert.assertNotNull("Large file not found in /api/files JSON response.", largeFileEntry)
        Assert.assertEquals(
            "Reported file size mismatch.",
            largeFileSize,
            largeFileEntry?.long("size")
        )
        tempFile.delete()

    }

    private fun createLargeFile(filename: String, sizeBytes: Long): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use the app's cache directory for a temporary file
        val tempFile = File(context.cacheDir, filename)

        if (tempFile.exists()) {
            tempFile.delete()
        }

        // Use RandomAccessFile and FileChannel to quickly size the file without holding 1GB in memory
        RandomAccessFile(tempFile, "rw").use { raf ->
            // This sets the file size without writing all the data
            raf.setLength(sizeBytes)
        }
        val filelen = tempFile.length()
        Assert.assertTrue(
            "Failed to create file of correct size: $filelen (got $sizeBytes)",
            filelen == sizeBytes
        )
        return tempFile
    }

}
