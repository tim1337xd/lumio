# Credits

## Credits

| Who | What it was | License |
| --- | --- | --- |
| [Fabric](https://fabricmc.net) (Loader, API, Loom) | The mod loader, the API and the build plugin the mod is built on. Not bundled: players install them | Apache-2.0 |
| [Inter](https://rsms.me/inter/) | The font of the menus and the HUD (Regular, SemiBold, Bold), bundled | OFL 1.1 |
| [Kalam](https://fonts.google.com/specimen/Kalam) | A handwritten font for lyrics in the world, bundled | OFL 1.1 |
| [Poppins](https://fonts.google.com/specimen/Poppins) | A font for lyrics in the world, bundled | OFL 1.1 |
| [GNU Unifont](https://unifoundry.com/unifont/) | The shapes of some of the server's symbols (such as the gemstone and star), redrawn larger in the mod's symbol pictures from the Unifont that comes with the game | OFL 1.1 or GPL-2.0-or-later with the font exception |
| [LRCLIB](https://lrclib.net) | Synced lyrics (see below) | Service |
| [Spotify Web API](https://developer.spotify.com) | What plays on Spotify (see below) | Service |
| [Cider](https://cider.sh) | What plays on Apple Music, and its lyrics (see below) | Local player |
| [iTunes Search API](https://performance-partners.apple.com/search-api) | Song covers (see below) | Service |

The jar bundles only the Lumio API, which is part of this project (GPL-3.0-only), and the three fonts above, with their licenses:
[Inter](src/main/resources/font/OFL-Inter.txt), [Kalam](src/main/resources/font/OFL-Kalam.txt) and [Poppins](src/main/resources/font/OFL-Poppins.txt). Java libraries such as Gson and JNA come with Minecraft and are not bundled.

No textures or other assets of Mojang or Hypixel are in this repository. The main menu's world draws the game's own textures from the game you
have installed.

## Third-party services

Lumio asks these services only for the features below. Each one sees your IP address, as with any web request. Your Minecraft account and session
are never read or sent. Every request uses HTTPS.

| Service | Used for | Feature | What is sent | When | How to turn it off | Links |
| --- | --- | --- | --- | --- | --- | --- |
| LRCLIB | Synced lyrics | Music, Live lyrics | The song's title, artist, album and length | For each new song, while Music and Live lyrics are on and no other source had lyrics | Turn off Live lyrics or Music | [Site](https://lrclib.net) |
| iTunes Search API | A cover when the player reports none | Music, Online covers | The song's title and artist | Once for each new song without a cover, while Music and Online covers are on | Turn off Online covers or Music | [Service](https://performance-partners.apple.com/search-api), [privacy](https://www.apple.com/legal/privacy/) |
| Cover addresses | The cover image | Music | Nothing beyond the request for the image | When a player reports a cover address or the iTunes Search API finds one, HTTPS only | Turn off Music | Depends on the player, such as Spotify or Apple |
| Spotify (accounts and Web API) | Signing in, what plays, and play, pause, skip and seek | Music, with Spotify as the source | Your Client ID and sign-in tokens, and the commands you give | Only with Spotify as the source and after you signed in | Pick another source, or disconnect the account on the Music page | [Terms](https://developer.spotify.com/terms), [privacy](https://www.spotify.com/legal/privacy-policy/) |

### Local connections

These don't leave your computer:

- **Cider on this PC** (`localhost:10767`): what plays, playback commands and lyrics, with the token you created in Cider. Cider itself then talks
  to Apple Music with its own sign-in, for the song's Apple Music id.
- **Windows media controls and the Windows audio meter**: read in-process through Windows. No other program is started and nothing goes online.
- **Spotify sign-in callback** (`127.0.0.1:48721`): a short-lived local server that only this computer can reach, to receive the sign-in from your browser.

### Hypixel and other servers

On a server, the info bar's ping segment sends the game's own ping request, carrying only a timestamp. See "Using it on Hypixel" in the
[README](README.md).

Add-ons can contact other services. The [Lumio: Hypixel SkyBlock](https://github.com/tim1337xd/Lumio-SkyBlock) add-on lists its own.

## Not affiliated

Lumio isn't affiliated with or endorsed by any of these projects or services.
