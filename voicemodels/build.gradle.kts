// Play Asset Delivery install-time pack: the offline Vosk speech models (Apache-2.0,
// alphacephei.com), installed with the app from Play so voice works on first start with no
// download. CI (.github/workflows/build-apk.yml) puts the two zips in src/main/assets/vosk/
// before building; they are never committed (.gitignore). Without them (local builds) the pack
// holds only README.txt and the app downloads the models itself on first start.
//
// AGP's asset-pack plugin is already on the build classpath through the root project's
// com.android.application plugin, so it is applied here without a version.
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("voicemodels")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
