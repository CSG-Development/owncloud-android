package com.owncloud.android.domain.device

interface UrlSwitchingTrigger {

    fun startUrlSwitching(fromBackground: Boolean)

    fun stopUrlSwitching()
}
