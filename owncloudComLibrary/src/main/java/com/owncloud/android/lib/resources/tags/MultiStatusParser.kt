package com.owncloud.android.lib.resources.tags

import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object MultiStatusParser {

    private const val NS_DAV = "DAV:"
    private const val NS_OC = "http://owncloud.org/ns"

    fun parseFileIds(xml: String): List<String> {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader(xml))

        val fileIds = mutableListOf<String>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "response" && parser.namespace == NS_DAV) {
                parseResponseFileId(parser)?.let { fileIds.add(it) }
            }
        }
        return fileIds
    }

    private fun parseResponseFileId(parser: XmlPullParser): String? {
        var fileId: String? = null

        val depth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue

            if (parser.name == "id" && parser.namespace == NS_OC) {
                fileId = XmlUtils.readText(parser)
            }
        }
        return fileId
    }
}
