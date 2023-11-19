// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class JetFilmizle : MainAPI() {
    override var mainUrl            = "https://jetfilmizle.cx"
    override var name               = "JetFilmizle"
    override val hasMainPage        = true
    override var lang               = "tr"
    override val hasQuickSearch     = true
    override val hasDownloadSupport = true
    override val supportedTypes     = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/page/"                 to "Son Filmler",
        "${mainUrl}/netflix/page/"         to "Netflix",
        "${mainUrl}/editorun-secimi/page/" to "Editörün Seçimi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("article.movie").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("h4 a")?.text()?.substringBefore(" izle") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "${mainUrl}/filmara.php",
            referer = "${mainUrl}/",
            data    = mapOf("s" to query)
        ).document

        return document.select("article.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("section.movie-exp div.movie-exp-title")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("src"))
        val year        = Regex("""\\b(\\d{4})\\b""").find(document.selectXpath("//div[@class='yap']/strong[contains(text(), 'Vizyon')]").text())?.groupValues?.get(1)?.toIntOrNull()
        val description = document.selectFirst("section.movie-exp p.aciklama")?.text()?.trim()
        val tags        = document.select("section.movie-exp div.catss a").map { it.text() }
        val rating      = document.selectFirst("section.movie-exp div.imdb_puan span")?.text()?.split(" ")?.last()?.toRatingInt()
        val actors      = document.select("section.movie-exp div.oyuncu").map {
            Actor(it.selectFirst("div.name")!!.text(), fixUrlNull(it.selectFirst("img")!!.attr("src")))
        }

        val recommendations = document.select("div#benzers article").mapNotNull {
            val recName      = it.selectFirst("h2 a")?.text() ?: return@mapNotNull null
            val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("src"))

            newMovieSearchResponse(recName, recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.rating          = rating
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("JTF", "data » ${data}")
        val document = app.get(data).document

        document.select("div.film_part a").forEach {
            val source = it.selectFirst("span")?.text()?.trim() ?: return@forEach
            if (source.lowercase().contains("okru") || source.lowercase().contains("fragman")) return@forEach

            val movDoc = app.get(it.attr("href")).document
            var iframe = movDoc.selectFirst("div#movie iframe")?.attr("src")

            if (iframe != null) {
                if (iframe.startsWith("//")) iframe = "https:$iframe"
                Log.d("JTF", "iframe » ${iframe}")

                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            } else {
                val downloadLink = movDoc.selectFirst("div#movie p a")?.attr("href") ?: return@forEach
                Log.d("JTF", "downloadLink » ${downloadLink}")

                if (downloadLink.contains("pixeldrain")) {
                    callback.invoke(
                        ExtractorLink(
                            source  = "Download",
                            name    = "Download",
                            url     = downloadLink,
                            referer = data,
                            quality = Qualities.Unknown.value,
                            isM3u8  = downloadLink.contains(".m3u8")
                        )
                    )
                }
            }
        }

        return true
    }
}