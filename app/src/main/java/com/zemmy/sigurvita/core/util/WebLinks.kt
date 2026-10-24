package com.zemmy.sigurvita.core.util

import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto

/** External metadata sites that Kavita can store in `SeriesMetadataDto.webLinks`. */
enum class ExternalLinkProvider {
    MANGABAKA,
    COMIC_VINE
}

data class SeriesExternalLink(
    val provider: ExternalLinkProvider,
    val url: String
)

private data class LinkRule(
    val provider: ExternalLinkProvider,
    val pattern: Regex
)

// Kavita stores web links as a comma-separated string. Each rule only matches the
// canonical series/volume page shape so unrelated links on the same host are ignored.
private val LINK_RULES = listOf(
    LinkRule(
        ExternalLinkProvider.MANGABAKA,
        Regex("""^https?://(?:www\.)?mangabaka\.org/(?:[a-z-]+/)?\d+""", RegexOption.IGNORE_CASE)
    ),
    LinkRule(
        ExternalLinkProvider.COMIC_VINE,
        Regex("""^https?://(?:www\.)?comicvine\.(?:gamespot\.com|com)/""", RegexOption.IGNORE_CASE)
    )
)

/** Recognised external links, de-duplicated by provider and kept in webLinks order. */
fun SeriesMetadataDto.externalLinks(): List<SeriesExternalLink> =
    webLinks
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.mapNotNull { url ->
            LINK_RULES.firstOrNull { it.pattern.containsMatchIn(url) }
                ?.let { SeriesExternalLink(it.provider, url) }
        }
        ?.distinctBy { it.provider }
        .orEmpty()

/**
 * The single link shown next to the resume button.
 *
 * Manga libraries show MangaBaka, comic libraries show Comic Vine. Any other
 * recognised link is used as a last resort so a valid link is never hidden.
 */
fun SeriesMetadataDto.primaryExternalLink(isManga: Boolean): SeriesExternalLink? {
    val links = externalLinks()
    val preferred = if (isManga) {
        links.firstOrNull { it.provider == ExternalLinkProvider.MANGABAKA }
    } else {
        links.firstOrNull { it.provider == ExternalLinkProvider.COMIC_VINE }
    }
    return preferred ?: links.firstOrNull()
}
