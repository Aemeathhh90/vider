package com.kakaanime.provider

import com.kakaanime.provider.extractor.BrowserStreamResolver

object ProviderFactory {
    fun createRegistry(browserResolver: BrowserStreamResolver? = null): ProviderRegistry {
        return ProviderRegistry().apply {
            register(OtakudesuProvider(browserResolver))
            register(SamehadakuProvider(browserResolver))
            register(AnimeSailProvider(browserResolver))

            register(NativeHtmlProvider("anoboy", "Anoboy", 60, "https://anoboy.xyz", browserResolver))
            register(NativeHtmlProvider("kuronime", "Kuronime", 80, "https://kuronime.net", browserResolver))
            register(NativeHtmlProvider("oploverz", "Oploverz", 200, "https://oploverz.cc", browserResolver))
            register(NativeHtmlProvider("zoronime", "Zoronime", 50, "https://zoronime.com", browserResolver))

            register(RemoteSourceProviderV2("animasu", "Animasu", 30, "animasu"))
            register(RemoteSourceProviderV2("animeindo", "AnimeIndo", 40, "animeindo"))
            register(RemoteSourceProviderV2("animekompi", "AnimeKompi", 70, "animekompi"))
            register(RemoteSourceProviderV2("doronime", "Doronime", 100, "doronime"))
            register(RemoteSourceProviderV2("hunter-no-sekai", "Hunter no Sekai", 110, "hunter-no-sekai"))
            register(RemoteSourceProviderV2("gomunime", "Gomunime", 120, "gomunime"))
            register(RemoteSourceProviderV2("neonime", "NeoNime", 130, "neonime"))
            register(RemoteSourceProviderV2("ylnime", "YLNime", 140, "ylnime"))
            register(RemoteSourceProviderV2("nontonanimeid", "NontonAnimeID", 150, "nontonanimeid"))
            register(RemoteSourceProviderV2("animeisme", "Animeisme", 160, "animeisme"))
            register(RemoteSourceProviderV2("animeku", "Animeku", 170, "animeku"))
            register(RemoteSourceProviderV2("kuramanime", "Kuramanime", 210, "kura"))
            register(RemoteSourceProviderV2("wibudesu", "Wibudesu", 220, "wibudesu"))
            register(RemoteSourceProviderV2("meownime", "Meownime", 230, "meownime"))
            register(RemoteSourceProviderV2("anibatch", "Anibatch", 240, "anibatch"))
            register(RemoteSourceProviderV2("nimegami", "Nimegami", 250, "nimegami"))
            register(RemoteSourceProviderV2("drivenime", "Drivenime", 260, "drivenime"))
            register(RemoteSourceProviderV2("anitoki", "Anitoki", 270, "anitoki"))
            register(RemoteSourceProviderV2("riie", "RiiE", 280, "riie"))
            register(RemoteSourceProviderV2("kusonime", "Kusonime", 290, "kusonime"))
            register(RemoteSourceProviderV2("animekuindo", "Animekuindo", 300, "animekuindo"))
            register(RemoteSourceProviderV2("allanime", "AllAnime", 320, "allanime"))
        }
    }

    fun createEngine(browserResolver: BrowserStreamResolver? = null): ProviderEngine = ProviderEngine(createRegistry(browserResolver))
}
