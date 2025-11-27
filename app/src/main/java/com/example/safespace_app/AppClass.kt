package com.example.safespace_app

import com.imagekit.android.ImageKit
import com.imagekit.android.entity.TransformationPosition
import com.imagekit.android.entity.UploadPolicy

class AppClass : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        ImageKit.init(
            context = applicationContext,
            publicKey = "public_dJcC+RalZmnq0VIlwC4IROwnxZ0=",
            urlEndpoint = "https://ik.imagekit.io/mkwp5d8ea",
            transformationPosition = TransformationPosition.PATH,
            defaultUploadPolicy = UploadPolicy.Builder()
                .requireNetworkType(UploadPolicy.NetworkType.ANY)
                .build()
        )
    }
}
