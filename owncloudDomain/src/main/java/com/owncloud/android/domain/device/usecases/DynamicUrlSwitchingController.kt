package com.owncloud.android.domain.device.usecases

interface DynamicUrlSwitchingController {

    fun initDynamicUrlSwitching()

    fun startDynamicUrlSwitching(fromBackground: Boolean)
    
    fun stopDynamicUrlSwitching()
}

