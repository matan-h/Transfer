package com.matanh.transfer.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import com.github.alexzhirkevich.customqrgenerator.vector.createQrVectorOptions
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColor
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorPixelShape
import com.google.android.material.color.MaterialColors

object QRCodeGenerator {

    @SuppressLint("Range")
    fun generateQRCode(context: Context, url: String): Drawable {
        val data = QrData.Url(url)

        val options = createQrVectorOptions {

            padding = .125f

            colors {
                dark = QrVectorColor.Solid(
                    MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorSecondary, Color.BLACK
                    )
                )
                frame = QrVectorColor.Solid(
                    MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorSecondary, Color.BLACK
                    )
                )

            }
            shapes {
                darkPixel = QrVectorPixelShape.RoundCorners(.5f)
                ball = QrVectorBallShape.RoundCorners(1f)
                frame = QrVectorFrameShape.RoundCorners(1f)
            }
        }

        return QrCodeDrawable(data, options)
    }
}