package com.owncloud.android.data.files.metadata

import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.owncloud.android.domain.files.model.ImageMetadata
import com.owncloud.android.domain.files.model.MetadataProperty
import com.owncloud.android.domain.files.model.MetadataSection
import com.owncloud.android.domain.files.model.MetadataSectionType

internal object ImageMetadataMapper {

    private val imageDetailsIfd0Tags = setOf(
        ExifIFD0Directory.TAG_IMAGE_WIDTH,
        ExifIFD0Directory.TAG_IMAGE_HEIGHT,
        ExifIFD0Directory.TAG_COMPRESSION,
        ExifIFD0Directory.TAG_PHOTOMETRIC_INTERPRETATION,
        ExifIFD0Directory.TAG_COLOR_SPACE,
    )

    private val imageDetailsSubIfdTags = setOf(
        ExifSubIFDDirectory.TAG_COMPONENTS_CONFIGURATION,
    )

    private val cameraDetailsTags = setOf(
        ExifIFD0Directory.TAG_MAKE,
        ExifIFD0Directory.TAG_MODEL,
        ExifSubIFDDirectory.TAG_LENS_MAKE,
        ExifSubIFDDirectory.TAG_LENS_MODEL,
        ExifSubIFDDirectory.TAG_LENS_SPECIFICATION,
    )

    private val captureSettingsTags = setOf(
        ExifSubIFDDirectory.TAG_EXPOSURE_TIME,
        ExifSubIFDDirectory.TAG_FNUMBER,
        ExifSubIFDDirectory.TAG_ISO_EQUIVALENT,
        ExifSubIFDDirectory.TAG_FOCAL_LENGTH,
        ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM,
        ExifSubIFDDirectory.TAG_EXPOSURE_BIAS,
        ExifSubIFDDirectory.TAG_METERING_MODE,
        ExifSubIFDDirectory.TAG_WHITE_BALANCE,
        ExifSubIFDDirectory.TAG_FLASH,
    )

    private val exifAuxTags = setOf(
        ExifSubIFDDirectory.TAG_BODY_SERIAL_NUMBER,
        ExifSubIFDDirectory.TAG_LENS_SERIAL_NUMBER,
        ExifSubIFDDirectory.TAG_CAMERA_OWNER_NAME,
    )

    private val timeTags = setOf(
        ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
        ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED,
        ExifIFD0Directory.TAG_DATETIME,
    )

    private val iptcTags = setOf(
        IptcDirectory.TAG_BY_LINE,
        IptcDirectory.TAG_CREDIT,
        IptcDirectory.TAG_CAPTION,
        IptcDirectory.TAG_KEYWORDS,
        IptcDirectory.TAG_COPYRIGHT_NOTICE,
    )

    private val tiffTags = setOf(
        ExifIFD0Directory.TAG_ORIENTATION,
        ExifIFD0Directory.TAG_X_RESOLUTION,
        ExifIFD0Directory.TAG_Y_RESOLUTION,
        ExifIFD0Directory.TAG_RESOLUTION_UNIT,
        ExifIFD0Directory.TAG_SOFTWARE,
        ExifIFD0Directory.TAG_ARTIST,
        ExifIFD0Directory.TAG_HOST_COMPUTER,
    )

    private val gpsTags = setOf(
        GpsDirectory.TAG_LATITUDE,
        GpsDirectory.TAG_LONGITUDE,
        GpsDirectory.TAG_ALTITUDE,
        GpsDirectory.TAG_IMG_DIRECTION,
    )

    fun map(metadata: Metadata): ImageMetadata {
        val usedTags = mutableSetOf<Pair<Class<*>, Int>>()
        val sections = mutableListOf<MetadataSection>()

        val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
        val iptc = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)

        buildDirectorySection(
            type = MetadataSectionType.IMAGE_DETAILS,
            directories = listOfNotNull(ifd0, subIfd),
            tagSets = listOf(imageDetailsIfd0Tags, imageDetailsSubIfdTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        buildGpsSection(gps, usedTags)?.let { sections.add(it) }

        buildDirectorySection(
            type = MetadataSectionType.CAMERA_DETAILS,
            directories = listOfNotNull(ifd0, subIfd),
            tagSets = listOf(cameraDetailsTags, cameraDetailsTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        buildDirectorySection(
            type = MetadataSectionType.CAPTURE_SETTINGS,
            directories = listOfNotNull(subIfd),
            tagSets = listOf(captureSettingsTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        buildDirectorySection(
            type = MetadataSectionType.EXIF_AUX_INFO,
            directories = listOfNotNull(subIfd),
            tagSets = listOf(exifAuxTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        buildDirectorySection(
            type = MetadataSectionType.TIME,
            directories = listOfNotNull(ifd0, subIfd),
            tagSets = listOf(timeTags, timeTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        buildDirectorySection(
            type = MetadataSectionType.AUTHORING_IPTC,
            directories = listOfNotNull(iptc),
            tagSets = listOf(iptcTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        buildDirectorySection(
            type = MetadataSectionType.TIFF,
            directories = listOfNotNull(ifd0),
            tagSets = listOf(tiffTags),
            usedTags = usedTags,
        )?.let { sections.add(it) }

        return ImageMetadata(sections = sections)
    }

    private fun buildGpsSection(
        gps: GpsDirectory?,
        usedTags: MutableSet<Pair<Class<*>, Int>>,
    ): MetadataSection? {
        if (gps == null || !hasValidGpsCoordinates(gps)) return null

        val properties = gpsTags.mapNotNull { tagType ->
            propertyFromTag(gps, tagType, usedTags)
        }
        return properties.takeIf { it.isNotEmpty() }?.let {
            MetadataSection(type = MetadataSectionType.GPS_LOCATION, properties = it)
        }
    }

    private fun hasValidGpsCoordinates(gps: GpsDirectory): Boolean {
        val geoLocation = gps.geoLocation ?: return false
        return geoLocation.latitude != 0.0 || geoLocation.longitude != 0.0
    }

    private fun buildDirectorySection(
        type: MetadataSectionType,
        directories: List<Directory>,
        tagSets: List<Set<Int>>,
        usedTags: MutableSet<Pair<Class<*>, Int>>,
    ): MetadataSection? {
        val properties = mutableListOf<MetadataProperty>()
        directories.forEachIndexed { index, directory ->
            val tags = tagSets.getOrElse(index) { tagSets.last() }
            tags.forEach { tagType ->
                propertyFromTag(directory, tagType, usedTags)?.let { properties.add(it) }
            }
        }
        return properties.takeIf { it.isNotEmpty() }?.let {
            MetadataSection(type = type, properties = it)
        }
    }

    private fun propertyFromTag(
        directory: Directory,
        tagType: Int,
        usedTags: MutableSet<Pair<Class<*>, Int>>,
    ): MetadataProperty? {
        val tagKey = directory.javaClass to tagType
        if (tagKey in usedTags || !directory.containsTag(tagType)) return null

        val value = directory.getDescription(tagType)?.trim().orEmpty()
        if (value.isBlank()) return null

        usedTags.add(tagKey)
        val label = directory.getTagName(tagType).ifBlank { "Tag $tagType" }
        return MetadataProperty(label = label, value = value)
    }
}
