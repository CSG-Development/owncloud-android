package com.owncloud.android.presentation.previews.text

/**
 * Fake [TextPreviewChunkRef] instances for Compose previews.
 * Offsets are synthetic; they do not point at a real file.
 */
object TextPreviewChunkRefFixtures {

    val singlePlain = TextPreviewChunkRef(
        id = 0,
        startByte = 0L,
        endByte = 2_048L,
        kind = TextPreviewChunkKind.Plain,
    )

    val plainFile: List<TextPreviewChunkRef> = listOf(
        TextPreviewChunkRef(id = 0, startByte = 0L, endByte = 8_192L, kind = TextPreviewChunkKind.Plain),
        TextPreviewChunkRef(id = 1, startByte = 8_192L, endByte = 16_384L, kind = TextPreviewChunkKind.Plain),
        TextPreviewChunkRef(id = 2, startByte = 16_384L, endByte = 24_576L, kind = TextPreviewChunkKind.Plain),
        TextPreviewChunkRef(id = 3, startByte = 24_576L, endByte = 30_000L, kind = TextPreviewChunkKind.Plain),
    )

    val markdownFile: List<TextPreviewChunkRef> = listOf(
        TextPreviewChunkRef(id = 0, startByte = 0L, endByte = 512L, kind = TextPreviewChunkKind.Markdown),
        TextPreviewChunkRef(id = 1, startByte = 512L, endByte = 4_096L, kind = TextPreviewChunkKind.Markdown),
        TextPreviewChunkRef(id = 2, startByte = 4_096L, endByte = 4_800L, kind = TextPreviewChunkKind.Markdown),
    )
}
