package com.kakaanime.provider

import com.kakaanime.provider.extractor.BrowserStreamResolver

object ProviderFactory {
    fun createRegistry(browserResolver: BrowserStreamResolver? = null): ProviderRegistry {
        return ProviderRegistry().apply {
            register(OtakudesuProvider(browserResolver))
            register(SamehadakuProvider(browserResolver))
            register(AnimeSailProvider(browserResolver))

            register(RemoteSourceProvider("animasu", "Animasu", 30, "animasu"))
            register(RemoteSourceProvider("animeindo", "AnimeIndo", 40, "animeindo"))
            register(RemoteSourceProvider("zoronime", "Zoronime", 50, "zoronime"))
            register(RemoteSourceProvider("anoboy", "Anoboy", 60, "anoboy"))
            register(RemoteSourceProvider("animekompi", "AnimeKompi", 70, "animekompi"))
            register(RemoteSourceProvider("kuronime", "Kuronime", 80, "kuronime"))
            register(RemoteSourceProvider("doronime", "Doronime", 100, "doronime"))
            register(RemoteSourceProvider("hunter-no-sekai", "Hunter no Sekai", 110, "hunter-no-sekai"))
            register(RemoteSourceProvider("gomunime", "Gomunime", 120, "gomunime"))
            register(RemoteSourceProvider("neonime", "NeoNime", 130, "neonime"))
            register(RemoteSourceProvider("ylnime", "YLNime", 140, "ylnime"))
            register(RemoteSourceProvider("nontonanimeid", "NontonAnimeID", 150, "nontonanimeid"))
            register(RemoteSourceProvider("animeisme", "Animeisme", 160, "animeisme"))
            register(RemoteSourceProvider("animeku", "Animeku", 170, "animeku"))
            register(RemoteSourceProvider("oploverz", "Oploverz", 200, "oploverz"))
            register(RemoteSourceProvider("kuramanime", "Kuramanime", 210, "kura"))
            register(RemoteSourceProvider("wibudesu", "Wibudesu", 220, "wibudesu"))
            register(RemoteSourceProvider("meownime", "Meownime", 230, "meownime"))
            register(RemoteSourceProvider("anibatch", "Anibatch", 240, "anibatch"))
            register(RemoteSourceProvider("nimegami", "Nimegami", 250, "nimegami"))
            register(RemoteSourceProvider("drivenime", "Drivenime", 260, "drivenime"))
            register(RemoteSourceProvider("anitoki", "Anitoki", 270, "anitoki"))
            register(RemoteSourceProvider("riie", "RiiE", 280, "riie"))
            register(RemoteSourceProvider("kusonime", "Kusonime", 290, "kusonime"))
            register(RemoteSourceProvider("animekuindo", "Animekuindo", 300, "animekuindo"))
            register(RemoteSourceProvider("allanime", "AllAnime", 320, "allanime"))
        }
    }

    fun createEngine(browserResolver: BrowserStreamResolver? = null): ProviderEngine = ProviderEngine(createRegistry(browserResolver))
}
