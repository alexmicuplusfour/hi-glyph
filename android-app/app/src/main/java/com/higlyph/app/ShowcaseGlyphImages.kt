package com.higlyph.app

import android.content.Context
import com.higlyph.app.models.CustomGlyphImage
import com.higlyph.app.repository.GlyphImageRepository
import com.higlyph.app.serialization.GlyphImageSerializer
import com.higlyph.app.toys.FrameBuilders
import com.higlyph.app.toys.PixelGrid

object ShowcaseGlyphImages {
    private const val SeedVersion = 1
    private const val SeedVersionKey = "showcase_images_seed_version"
    private const val ShowcaseImageIdPrefix = "showcase_"
    private const val StableTimestamp = 0L

    fun isShowcaseImage(image: CustomGlyphImage): Boolean {
        return image.id.startsWith(ShowcaseImageIdPrefix)
    }

    fun seedIfNeeded(context: Context, repository: GlyphImageRepository) {
        repository.seedImagesOnce(
            seedVersionKey = SeedVersionKey,
            version = SeedVersion,
            images = buildImages(context),
        )
    }

    private fun buildImages(context: Context): List<CustomGlyphImage> {
        return listOf(
            image(
                id = "${ShowcaseImageIdPrefix}app_logo",
                name = context.getString(R.string.showcase_matrix_lab_logo_name),
                grid = FrameBuilders.buildLabIconGrid(),
            ),
            image(
                id = "${ShowcaseImageIdPrefix}cross_example",
                name = context.getString(R.string.showcase_cross_example_name),
                grid = FrameBuilders.buildAppLogoGrid(),
            ),
            image(
                id = "${ShowcaseImageIdPrefix}dollar_example",
                name = context.getString(R.string.showcase_dollar_icon_name),
                grid = FrameBuilders.buildDollarIconGrid(),
            ),
        )
    }

    private fun image(id: String, name: String, grid: PixelGrid): CustomGlyphImage {
        return CustomGlyphImage(
            id = id,
            name = name,
            pixels = GlyphImageSerializer.pixelGridToBinary(grid),
            createdAt = StableTimestamp,
            updatedAt = StableTimestamp,
        )
    }
}
