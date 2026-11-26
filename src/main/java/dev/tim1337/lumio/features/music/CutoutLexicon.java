package dev.tim1337.lumio.features.music;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the Cut-out preset knows about words: which are small function words (articles, prepositions, pronouns, conjunctions) in English and
 * German, and which have a symbol. Our own lists, short on purpose. Plain logic, tested without the game.
 */
final class CutoutLexicon {
	/** A word without a symbol. */
	static final int NO_SYMBOL = -1;

	private static final Set<String> ENGLISH = Set.of("a", "an", "the", "and", "or", "but", "nor", "so", "yet", "if", "as", "at", "by", "for", "from", "in", "into", "of",
		"off", "on", "onto", "out", "over", "to", "up", "with", "than", "that", "this", "these", "those", "then", "i", "me", "my", "mine", "you", "your", "yours", "he",
		"him", "his", "she", "her", "hers", "it", "its", "we", "us", "our", "ours", "they", "them", "their", "is", "am", "are", "was", "were", "be", "been", "do", "does",
		"did", "have", "has", "had", "will", "would", "can", "could", "all", "im", "i'm", "it's", "i'll", "i've", "you're", "don't", "ain't", "oh", "ooh", "ah", "yeah",
		"like", "just", "when", "where", "who", "what", "how", "about", "there", "here", "not", "no");
	private static final Set<String> GERMAN = Set.of("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines", "und", "oder", "aber",
		"denn", "doch", "wenn", "als", "wie", "dass", "weil", "ob", "an", "auf", "aus", "bei", "bis", "durch", "für", "gegen", "in", "im", "ins", "mit", "nach", "ohne",
		"über", "um", "unter", "von", "vom", "vor", "zu", "zum", "zur", "ich", "mich", "mir", "mein", "meine", "meinen", "meiner", "du", "dich", "dir", "dein", "deine",
		"er", "ihn", "ihm", "sie", "ihr", "ihre", "es", "wir", "uns", "unser", "unsere", "euch", "euer", "ist", "bin", "bist", "sind", "war", "waren", "hab", "habe",
		"hast", "hat", "haben", "wird", "werden", "kann", "nicht", "kein", "keine", "nur", "noch", "schon", "so", "da", "wo", "was", "wer", "ja", "nein", "man", "am");

	/**
	 * The symbols, each with the English and German words it stands beside: the thing itself and what is said around it, with plurals, verb
	 * forms and common spellings, so that a song has a symbol every line or two. No word stands in two lists. The order is the order of the icons in their
	 * picture ({@link dev.tim1337.lumio.render.CutoutIcons}): a symbol's number is its place here.
	 */
	private static final String[][] SYMBOLS = {
		{"car",
			"porsche", "porsches", "benz", "benzes", "mercedes", "amg", "maybach", "bmw", "beamer", "beemer", "audi", "rs", "lambo", "lambos", "lamborghini", "ferrari", "ferraris", "rari", "raris", "bentley", "rolls", "royce", "gwagon", "cabrio", "coupe", "coupé", "sportwagen", "sportscar", "ps", "gas", "vroom", "autobahn", "speed",
			"cars", "auto", "autos", "wagen", "ride", "rides", "riding", "whip", "whips", "karre", "karren", "drive", "drives", "driving", "drove",
			"fahren", "fahre", "fährt", "fahr", "truck", "trucks", "wheels", "engine", "motor"},
		{"heart", "hearts", "herz", "herzen", "love", "loved", "loving", "loves", "lover", "lovers", "liebe", "lieben", "liebt", "liebst", "geliebt",
			"verliebt", "baby", "babe", "darling", "schatz", "liebling", "honey", "sweetheart", "beloved"},
		{"fire", "fires", "feuer", "flame", "flames", "flamme", "flammen", "burn", "burns", "burning", "burned", "brennt", "brennen", "brenne",
			"verbrannt", "hot", "heat", "heiß", "hitze", "blaze", "lit", "glut", "inferno", "hölle"},
		{"moon", "moons", "mond", "moonlight", "mondlicht", "mondschein", "lunar", "luna", "crescent", "vollmond", "moonshine", "moonlit", "mondnacht"},
		{"sun",
			"summertime", "sommertag",
			"suns", "sonne", "sonnen", "sunshine", "sunlight", "sonnenschein", "sunrise", "sunset", "sonnenaufgang", "sonnenuntergang", "sunny",
			"sonnig", "summer", "sommer", "day", "days", "tag", "tage", "daylight", "tageslicht"},
		{"star", "stars", "stern", "sterne", "sternen", "starlight", "sternenlicht", "superstar", "wish", "wishes", "wishing", "wunsch", "wünsche",
			"wünsch"},
		{"money",
			"racks", "bands", "stacks", "stack", "bread", "para", "patte", "batzen", "mio", "mios", "guap", "cheddar", "dough", "bargeld", "riches", "mille",
			"geld", "cash", "dollar", "dollars", "euro", "euros", "coin", "coins", "münze", "münzen", "rich", "reich", "bank", "kohle", "bucks",
			"pay", "paid", "paying", "zahlen", "bezahlt", "wealth", "million", "millions", "millionen", "bills", "scheine", "moneten", "knete", "pleite"},
		{"rain", "rains", "raining", "rainy", "regen", "regnet", "regnen", "pouring",
			"drizzle", "downpour", "regentropfen", "raindrops"},
		{"night", "nights", "nacht", "nächte", "tonight", "midnight", "mitternacht", "nighttime", "overnight", "evening", "abend", "abends", "dark",
			"darkness", "dunkel", "dunkelheit", "dusk", "nachts", "goodnight"},
		{"city",
			"kiez", "chicago", "kreuzberg", "streets", "ghetto", "bronx", "brooklyn", "harlem", "compton", "atlanta", "hamburg", "münchen", "köln", "wien", "zürich", "frankfurt",
			"cities", "stadt", "städte", "town", "towns", "downtown", "skyline", "hood", "block", "viertel", "boulevard", "metropolis", "großstadt",
			"hometown", "berlin", "york", "paris", "london", "tokyo", "vegas"},
		{"eye", "eyes", "auge", "augen", "see", "sees", "seeing", "saw", "seen", "sehe", "sehen", "sieht", "gesehen", "look", "looks", "looking", "looked",
			"schau", "schauen", "blick", "blicke", "watching", "stare", "staring", "vision", "view"},
		{"phone",
			"dm", "dms", "facetime", "nachricht", "nachrichten", "iphone", "ringtone",
			"phones", "telefon", "handy", "handys", "call", "calls", "calling", "called", "anruf", "anrufe", "anrufen", "ruf", "text", "texting",
			"texts", "dial", "hotline", "voicemail", "cellphone", "smartphone", "nummer", "number"},
		{"clock", "clocks", "hour", "hours", "stunde", "stunden", "minute", "minutes", "minuten",
			"second", "seconds", "sekunde", "sekunden", "late", "later", "spät", "später"},
		{"crown",
			"goat", "goats", "legend", "legends", "legende",
			"crowns", "krone", "kronen", "king", "kings", "könig", "könige", "queen", "queens", "königin", "royal", "royalty", "throne", "thron",
			"prince", "princess", "prinz", "prinzessin", "boss", "chef", "ruler", "kingdom", "königreich", "emperor", "kaiser"},
		{"road", "roads", "straße", "straßen", "street", "highway", "highways", "weg", "wege", "way", "ways", "path", "pfad", "route", "lane",
			"avenue", "journey", "reise", "travel", "traveling", "trip", "miles", "meilen", "kilometer"},
		{"home", "homes", "house", "houses", "haus", "häuser", "zuhause", "heim", "heimat", "hause", "daheim", "apartment", "wohnung", "room", "zimmer",
			"roof", "dach", "neighborhood", "nachbarschaft", "family", "familie", "mama", "mom", "mother", "mutter", "papa", "dad", "father", "vater"},
		{"song", "songs", "lied", "lieder", "melody", "melodie", "sing", "sings", "singing", "sang", "sung", "singen", "singt", "singe",
			"rhythm", "rhythmus", "tune", "tunes", "klang", "klänge", "chorus", "refrain", "note", "notes", "noten",
			"dance", "dancing", "danced", "dances", "tanzen", "tanz", "tanzt", "tanze"},
		{"drink", "drinks", "drinking", "drank", "drunk", "glass", "glas", "gläser", "wine", "wein", "champagne", "champagner", "cocktail", "cocktails",
			"sip", "sipping", "trinken", "trinke", "trinkt", "betrunken", "bar", "whiskey", "vodka", "wodka", "gin", "rum", "tequila", "cheers", "prost",
			"toast"},
		{"cloud", "clouds", "wolke", "wolken", "sky", "skies", "himmel", "cloudy", "bewölkt", "air", "luft", "breeze", "brise", "wind", "winds", "winde"},
		{"snow", "schnee", "winter", "eis", "cold", "kalt", "kälte", "frozen", "freeze", "freezing", "gefroren", "frost", "snowflake",
			"schneeflocke", "icy", "chill", "chilly", "december", "dezember", "snowing", "schneit"},
		{"lightning", "blitz", "blitze", "electric", "electricity", "elektrisch", "energy", "energie", "power", "powers", "kraft",
			"strom", "shock", "shocked", "schock", "spark", "sparks", "funke", "funken", "voltage", "charge", "charged"},
		{"tree", "trees", "baum", "bäume", "forest", "wald", "wälder", "woods", "jungle", "dschungel", "nature", "natur", "garden", "garten", "park",
			"branch", "branches", "ast", "äste", "roots", "wurzeln", "wood", "holz", "pine", "oak"},
		{"flower", "flowers", "blume", "blumen", "rose", "roses", "rosen", "bloom", "blooming", "blüte", "blüten", "blühen", "blossom", "petals", "daisy",
			"tulip", "tulpe", "lily", "bouquet", "strauß", "spring", "frühling"},
		{"wave", "waves", "welle", "wellen", "ocean", "oceans", "ozean", "sea", "seas", "meer", "meere", "water", "waters", "wasser", "river", "rivers",
			"fluss", "flüsse", "beach", "strand", "shore", "ufer", "tide", "flut", "swim", "swimming", "schwimmen", "surf", "lake", "pool"},
		{"mountain", "mountains", "berg", "berge", "hill", "hills", "hügel", "peak", "gipfel", "summit", "climb", "climbing", "klettern", "cliff",
			"klippe", "valley", "tal", "rocks", "felsen", "stone", "stones", "stein", "alps", "alpen"},
		{"bird", "birds", "vogel", "vögel", "feather", "federn", "feder", "nest",
			"dove", "taube", "eagle", "adler", "free", "freedom", "frei", "freiheit", "soar", "soaring"},
		{"plane",
			"fly", "flies", "flew", "flying", "fliegen", "fliege", "fliegt", "flog", "flieger", "privatjet", "firstclass", "jetlag",
			"planes", "flugzeug", "flugzeuge", "airplane", "airplanes", "jet", "jets", "flight", "flights", "flug", "flüge", "airport", "flughafen",
			"pilot", "takeoff", "landing", "abheben", "landen", "airline", "runway", "boarding", "departure"},
		{"train", "trains", "zug", "züge", "bahn", "station", "bahnhof", "railway", "railroad", "tracks", "gleis", "gleise", "subway", "metro", "ubahn",
			"tram", "locomotive", "wagon", "waggon", "platform", "bahnsteig", "ticket", "tickets"},
		{"boat", "boats", "boot", "boote", "ship", "ships", "schiff", "schiffe", "sail", "sails", "sailing", "sailor", "segeln", "segel", "yacht",
			"captain", "kapitän", "harbor", "hafen", "cruise", "deck", "matrose", "voyage"},
		{"bike", "bikes", "fahrrad", "fahrräder", "bicycle", "bicycles", "rad", "cycle", "cycling", "radfahren", "pedal", "pedals", "biker", "moped",
			"scooter", "roller", "motorbike", "motorcycle", "motorrad", "wheelie", "bmx"},
		{"key", "keys", "schlüssel", "unlock", "unlocked", "open", "opened", "opening", "öffnen", "öffne", "offen", "aufmachen", "access", "answer",
			"answers", "antwort", "antworten", "solution", "lösung"},
		{"lock", "locks", "locked", "schloss", "secret", "secrets", "geheimnis", "geheimnisse", "geheim", "safe", "sicher", "closed", "verschlossen",
			"abgeschlossen", "cage", "käfig", "prison", "gefängnis", "trapped", "gefangen", "hide", "hiding",
			"verstecken"},
		{"door", "doors", "tür", "türen", "doorway", "entrance", "eingang", "exit", "ausgang", "gate", "gates", "tor", "tore", "knock", "knocking",
			"klopfen", "klopf", "leave", "leaving", "geh", "gehen", "gegangen", "gone", "goodbye", "bye", "tschüss", "abschied", "farewell"},
		{"bed", "beds", "bett", "betten", "sleep", "sleeping", "slept", "asleep", "schlaf", "schlafen", "schläft", "schlafe", "dream", "dreams",
			"dreaming", "dreamed", "traum", "träume", "träumen", "träumt", "pillow", "kissen", "tired", "müde", "nap", "blanket", "decke", "nightmare",
			"albtraum"},
		{"book", "books", "buch", "bücher", "story", "stories", "geschichte", "geschichten", "page", "pages", "seite", "seiten", "read", "reading",
			"lesen", "lese", "liest", "chapter", "kapitel", "write", "writing", "wrote", "written", "schreiben", "schreibe", "word", "words", "wort", "worte",
			"wörter", "poem", "gedicht", "diary", "tagebuch"},
		{"letter", "letters", "brief", "briefe", "mail", "message", "messages", "envelope", "umschlag", "postcard",
			"postkarte", "email", "post", "send", "sending", "sent", "schicken", "schick", "senden", "inbox"},
		{"camera",
			"fame", "famous", "berühmt", "ruhm", "blitzlicht",
			"cameras", "kamera", "kameras", "photo", "photos", "foto", "fotos", "picture", "pictures", "bild", "bilder", "selfie", "selfies",
			"snapshot", "flash", "polaroid", "pose", "posing", "paparazzi", "lens", "frame", "memory", "memories", "erinnerung", "erinnerungen"},
		{"ring", "rings", "ringe", "wedding", "hochzeit", "marry", "married", "marriage", "heiraten", "verheiratet", "engaged", "verlobt", "verlobung",
			"bride", "braut", "groom", "vow", "vows", "promise", "promises", "versprechen", "versprochen", "husband", "wife", "ehemann", "ehefrau"},
		{"diamond",
			"ice", "iced", "chain", "chains", "kette", "ketten", "klunker", "steine", "glänzen", "glänzt", "sparkle", "sparkling", "shiny", "glitzer", "schmuck", "jewelry", "jewellery",
			"diamonds", "diamant", "diamanten", "jewel", "jewels", "juwel", "juwelen", "gem", "gems", "edelstein", "treasure",
			"silver", "silber", "bling", "carat", "karat", "luxury", "luxus", "precious", "wertvoll", "pearls", "perlen", "necklace"},
		{"gift", "gifts", "geschenk", "geschenke", "present", "presents", "birthday", "geburtstag", "christmas", "weihnachten", "surprise", "überraschung",
			"give", "giving", "gave", "given", "geben", "gebe", "gibt", "schenken", "schenk", "wrapped", "santa", "xmas"},
		{"coffee", "kaffee", "tea", "tee", "cup", "cups", "tasse", "tassen", "morning", "mornings", "morgen", "morgens", "breakfast", "frühstück", "wake",
			"waking", "woke", "awake", "wach", "aufwachen", "espresso", "latte", "cafe", "café", "mug"},
		{"tear", "tears", "träne", "tränen", "cry", "cries", "crying", "cried", "weinen", "weine", "weint", "geweint", "sad", "sadness", "traurig",
			"trauer", "sorrow", "kummer", "sob", "weep", "weeping", "heulen", "lonely", "alone", "einsam", "allein"},
		{"lips", "lip", "lippen", "lippe", "kiss", "kisses", "kissing", "kissed", "kuss", "küsse", "küssen", "küsst", "geküsst", "mouth", "mund", "taste",
			"tasting", "geschmack", "schmecken", "lipstick", "lippenstift", "whisper", "whispering", "flüstern", "tongue"},
		{"smile", "smiles", "smiling", "smiled", "lächeln", "lächelt", "lächle", "laugh", "laughs", "laughing", "laughed", "lachen", "lacht", "lache",
			"happy", "happiness", "glücklich", "froh", "joy", "freude", "fun", "spaß", "glad", "cheerful", "grin"},
		{"hand", "hands", "hände", "touch", "touching", "touched", "berühren", "berührt", "hold", "holding", "held", "halten", "halte", "hält", "fingers",
			"finger", "grab", "reach", "reaching", "clap", "klatschen", "palm", "grip", "greifen"},
		{"shoe",
			"kicks", "jordans", "jordan", "nikes", "nike", "adidas", "sneaker", "yeezy", "yeezys", "turnschuhe", "airmax",
			"shoes", "schuh", "schuhe", "walk", "walking", "walked", "run", "running", "ran", "laufen", "laufe", "läuft", "rennen", "renne", "rennt",
			"step", "steps", "schritt", "schritte", "sneakers", "boots", "stiefel", "feet", "foot", "füße", "fuß", "heels"},
		{"wings", "wing", "flügel", "angel", "angels", "engel", "heaven", "heavens", "holy", "heilig", "halo", "divine",
			"göttlich", "saint"},
		{"ghost", "ghosts", "geist", "geister", "haunted", "haunt", "haunting", "shadow", "shadows", "schatten", "spooky", "scary", "scared", "angst",
			"fear", "fears", "afraid", "furcht", "monster", "monsters", "demon", "demons", "dämon", "dämonen", "phantom", "boo"},
		{"world", "worlds", "welt", "welten", "earth", "erde", "globe", "planet", "planets", "planeten", "everywhere", "überall", "global", "worldwide",
			"weltweit", "map", "karte", "continent", "around", "international", "mankind", "menschheit", "humanity"},
		{"flag", "flags", "flagge", "flaggen", "fahne", "fahnen", "country", "countries", "land", "länder", "nation", "nations", "anthem", "hymne",
			"banner", "patriot", "border", "grenze", "grenzen", "homeland"},
		{"radio", "radios", "stereo", "speaker", "speakers", "lautsprecher", "bass", "volume", "lautstärke", "loud", "louder", "laut", "lauter", "boombox",
			"fm", "broadcast", "frequency", "frequenz", "noise", "lärm", "amplifier", "verstärker", "subwoofer"},
		{"tv", "television", "fernseher", "fernsehen", "screen", "screens", "bildschirm", "movie", "movies", "film", "filme", "cinema",
			"kino", "channel", "kanal", "series", "serie", "episode", "hollywood", "scene", "szene", "actor", "drama"},
		{"guitar", "guitars", "gitarre", "gitarren", "band", "rock", "rockstar", "strings", "saiten", "chord", "chords", "akkord", "akkorde", "riff",
			"solo", "acoustic", "akustik", "strum", "blues", "punk", "metal"},
		{"microphone",
			"flow", "flows", "bars", "verse", "verses", "strophe", "strophen", "beat", "beats", "studio", "studios", "track", "freestyle", "cypher", "spit", "spitting", "booth",
			"microphones", "mikrofon", "mikrofone", "mic", "mics", "voice", "voices", "stimme", "stimmen", "rap", "rapper", "rapping", "rappen",
			"rhyme", "rhymes", "reim", "reime", "speak", "speaking", "spoke", "sprechen", "spricht", "say", "saying", "said", "sagen", "sagt", "sag", "tell",
			"telling", "told", "erzähl", "erzählen", "talk", "talking", "reden", "rede", "shout", "scream", "schreien", "schrei"},
		{"ball", "balls", "bälle", "game", "games", "spiel", "spiele", "play", "playing", "played", "player", "players", "spielen", "spielt", "spieler",
			"goal", "goals", "score", "team", "match", "football", "fußball", "soccer", "basketball", "kick", "court"},
		{"dice", "würfel", "luck", "lucky", "glück", "chance", "chances", "gamble", "gambling", "bet", "bets", "betting", "wette", "wetten", "risk",
			"risiko", "casino", "poker", "jackpot", "roll", "rolling", "fate", "schicksal", "fortune", "odds"},
		{"rocket",
			"rise", "rises", "rising", "top", "hoch", "oben", "steigen", "steige", "steigt", "aufstieg", "skyrocket",
			"rockets", "rakete", "raketen", "space", "weltall", "weltraum", "universe", "universum", "galaxy", "galaxie", "galaxies", "astronaut",
			"orbit", "launch", "mars", "cosmos", "kosmos", "alien", "aliens", "ufo", "satellite", "satellit", "gravity", "schwerkraft"},
		{"umbrella", "umbrellas", "regenschirm", "schirm", "schirme", "shelter", "schutz", "protect", "protecting", "protected", "beschützen", "schützen",
			"cover", "covered", "geschützt", "behütet", "dry", "trocken", "weather", "wetter"},
		{"candle", "candles", "kerze", "kerzen", "gebete", "hope",
			"hoping", "hoffnung", "hoffe", "hoffen", "wax", "wachs", "flicker", "memorial", "vigil", "andacht"},
		{"light", "lights", "licht", "lichter", "bulb", "idea", "ideas", "idee", "ideen", "bright", "brighter", "shine", "shining", "shines", "scheinen",
			"scheint", "glow", "glowing", "leuchten", "leuchtet", "think", "thinking", "thought", "thoughts", "denken", "denke", "denkt", "gedanke",
			"gedanken", "mind", "kopf", "brain", "know", "knows", "knowing", "knew", "wissen", "weiß", "lamp", "lampe", "neon"},
		{"heartbreak",
			"brokenhearted", "liebeskummer",
			"heartbroken", "heartache", "herzschmerz", "broken", "break", "breaking", "broke", "brechen", "gebrochen", "bricht", "zerbrochen",
			"hurt", "hurts", "hurting", "pain", "schmerz", "schmerzen", "weh", "wehtun", "vorbei", "ex", "breakup", "trennung", "getrennt", "miss", "missing",
			"missed", "vermissen", "vermisse", "vermisst", "sorry", "verzeih"},
		{"bottle", "bottles", "flasche", "flaschen", "beer", "beers", "bier", "biere", "liquor", "alkohol", "alcohol", "booze", "pour", "poured",
			"bottled", "sixpack", "brew", "ale", "pils", "cork", "korken", "saufen", "hangover"},
		{"sunglasses", "shades", "sonnenbrille", "cool", "cooler", "coolest", "swag", "stil", "fresh", "frisch",
			"vibe", "vibes", "chillen", "lässig", "locker", "flex", "flexing"},
		{"headphones",
			"music", "musik", "sound", "sounds", "playlist", "playlists", "spotify",
			"headphone", "kopfhörer", "earphones", "earbuds", "listen", "listening", "listened", "hören", "höre", "hört", "gehört", "zuhören",
			"hear", "hearing", "heard", "podcast", "audio", "ears", "ear", "ohr", "ohren", "stream"},
		{"cat", "cats", "katze", "katzen", "kitty", "kitten", "kittens", "kätzchen", "meow", "miau", "purr", "purring", "schnurren", "tiger", "lion",
			"löwe", "löwen", "panther", "wildcat", "feline", "kater"},
		{"dog", "dogs", "hund", "hunde", "puppy", "puppies", "welpe", "welpen", "bark", "barking", "bellen", "wolf", "wolves", "wölfe", "pup", "hound",
			"doggy", "loyal", "treu", "treue", "pet", "pets", "haustier", "bone", "knochen", "paw", "pfote"},
		{"fish", "fishes", "fisch", "fische", "shark", "sharks", "hai", "haie", "fishing", "angeln", "whale", "wal", "wale", "dolphin", "delfin",
			"mermaid", "meerjungfrau", "deep", "tief", "tiefe", "underwater", "unterwasser", "dive", "diving", "tauchen", "aquarium", "bait", "köder"},
		{"butterfly", "butterflies", "schmetterling", "schmetterlinge", "flutter", "fluttering", "flattern", "change", "changes", "changing", "changed",
			"ändern", "verändern", "verändert", "wandel", "transform", "cocoon", "kokon", "caterpillar", "raupe", "delicate", "zart", "nervous", "nervös",
			"bauchkribbeln"},
		{"leaf", "leaves", "blatt", "blätter", "autumn", "herbst", "fall", "falling", "fell", "fallen", "fällt", "green", "grün", "grow", "growing",
			"grew", "grown", "wachsen", "wächst", "gewachsen", "plant", "plants", "pflanze", "pflanzen", "seed", "seeds", "samen", "october", "oktober",
			"november"},
		{"apple", "apples", "apfel", "äpfel", "fruit", "fruits", "frucht", "früchte", "obst", "cherry", "cherries", "kirsche", "kirschen", "peach",
			"pfirsich", "sweet", "süß", "süße", "sugar", "zucker", "candy", "bonbon", "bite", "biting", "beißen", "biss", "juicy", "saftig", "eat", "eating",
			"ate", "essen", "esse", "isst", "food", "hungry", "hunger"},
		{"icecream", "eiscreme", "cone", "waffel", "vanilla", "vanille", "chocolate", "schokolade", "strawberry", "erdbeere", "dessert", "nachtisch",
			"treat", "treats", "lick", "lecken", "melt", "melting", "melted", "schmelzen", "schmilzt", "geschmolzen", "scoop", "kugel", "sundae", "sorbet",
			"gelato", "creamy", "cremig"},
		{"cake", "cakes", "kuchen", "torte", "torten", "celebrate", "celebrating", "celebration", "feiern", "feier", "gefeiert", "anniversary",
			"jahrestag", "bake", "baking", "backen", "baked", "slice", "stück", "frosting", "cupcake", "cupcakes", "muffin", "pie", "cookie", "cookies",
			"keks", "kekse"},
		{"balloon", "balloons", "ballon", "ballons", "luftballon", "party", "parties", "partys", "club", "clubs", "confetti", "konfetti",
			"disco", "rave", "weekend", "wochenende", "friday", "freitag", "saturday", "samstag", "guests", "gäste"},
		{"trophy",
			"gold", "golden", "platinum", "platin", "numberone",
			"trophies", "trophäe", "pokal", "win", "wins", "winning", "won", "winner", "winners", "gewinnen", "gewinne", "gewinnt", "gewonnen",
			"gewinner", "sieg", "siege", "sieger", "victory", "champion", "champions", "champ", "first", "erster", "best", "beste", "bester", "medal",
			"medaille", "prize", "preis"},
		{"bag", "bags", "tasche", "taschen", "sack", "bündel", "loot", "beute", "profit", "profits", "gewinn", "income",
			"einkommen", "salary", "gehalt", "paycheck", "lohn", "budget", "savings", "ersparnisse", "vermögen",
			"business", "geschäft", "deal", "deals"},
		{"hourglass",
			"ewigkeit", "time", "times", "zeit", "zeiten", "wait", "waiting", "waited", "warte", "warten", "forever", "ewig", "eternity", "eternal",
			"sanduhr", "sand", "patience", "geduld", "geduldig", "patient", "slow", "slowly", "slower", "langsam", "langsamer", "past",
			"vergangenheit", "future", "zukunft", "history", "ages", "age", "alter", "old", "older", "alt", "älter", "young", "younger", "jung", "jünger",
			"years", "year", "jahr", "jahre", "months", "month", "monat", "monate", "weeks", "week", "woche", "wochen"},
		{"place", "places", "ort", "orte", "platz", "plätze", "location", "somewhere", "irgendwo", "nowhere", "nirgendwo", "anywhere", "spot", "address",
			"adresse", "destination", "ziel", "ziele", "find", "finding", "found", "finden", "finde", "findet", "gefunden", "lost", "verloren", "verlaufen",
			"direction", "richtung"},
		{"anchor", "anchors", "anker", "stay", "stays", "staying", "stayed", "bleib", "bleiben", "bleibt", "bleibe", "geblieben", "steady",
			"stronger", "stärke", "ground", "boden", "grounded", "stable", "stabil", "firm", "fest", "weight", "gewicht", "heavy",
			"schwer"},
		{"bell", "bells", "glocke", "glocken", "ringing", "rang", "klingeln", "klingelt", "wecker", "alert", "warning", "warnung",
			"signal", "chime", "chimes", "ding", "dong", "läuten", "läutet", "school", "schule", "reminder", "notification"},
		{"gamepad", "controller", "gaming", "gamer", "gamers", "zocken", "zocke", "zockt", "console", "konsole", "level", "levels", "pixel", "pixels",
			"arcade", "joystick", "quest", "respawn", "online", "offline", "lobby", "videogame", "videogames", "videospiel", "videospiele", "highscore",
			"cheat", "noob"},
		// added 2026-11-17: smoke (a plain cloud of smoke, nothing more), watch, hanger, praying hands, storm cloud, record, siren, palm tree,
		// spotlight, dumbbell; brand names stand for the thing in general, never for the brand
		{"smoke", "smokes", "smoking", "smoked", "smokin", "smoky", "haze", "hazy", "fog", "foggy", "nebel", "neblig", "mist", "rauch", "rauchen",
			"rauche", "raucht", "qualm", "qualmen", "shisha", "dampf", "dampfen", "vapor", "steam"},
		{"wristwatch", "watch", "watches", "rolex", "rolexes", "ap", "patek", "audemars", "wrist", "wrists", "uhr", "uhren", "armbanduhr",
			"handgelenk", "timepiece", "tick", "ticking", "ticken", "chrono", "chronograph"},
		{"hanger", "kleiderbügel", "drip", "dripping", "fit", "fits", "outfit", "outfits", "designer", "gucci", "prada", "louis", "vuitton",
			"balenciaga", "versace", "dior", "chanel", "fashion", "klamotten", "kleider", "kleid", "dress", "dresses", "style", "styled", "stylish",
			"wardrobe", "kleiderschrank", "mode", "jacket", "jacke"},
		{"pray", "prays", "praying", "prayed", "prayer", "prayers", "god", "gott", "jesus", "christ", "lord", "faith", "glaube", "glauben", "blessed",
			"bless", "blessing", "blessings", "gesegnet", "segen", "segnen", "beten", "bete", "betet", "gebet", "amen", "church", "kirche"},
		{"storm", "storms", "thunderstorms", "stürmen", "stormy", "sturm", "stürme", "stürmisch", "gewitter", "thunder", "thunderstorm", "donner", "donnern", "unwetter",
			"hurricane", "orkan", "tornado", "tempest", "hail", "hagel", "wirbelsturm"},
		{"record", "records", "vinyl", "album", "albums", "platte", "platten", "schallplatte", "lp", "mixtape", "mixtapes", "tape", "tapes", "single",
			"singles", "debut", "turntable", "plattenspieler", "dj", "spin", "spinning"},
		{"siren", "sirens", "polizist", "polizisten", "sheriff", "sirene", "sirenen", "police", "polizei", "cops", "cop", "blaulicht", "alarm", "alarms", "emergency", "notfall",
			"ambulance", "krankenwagen", "feds", "bullen", "streife", "chase"},
		{"palmtree", "palmtrees", "palme", "palmen", "vacation", "urlaub", "holiday", "holidays", "island", "islands", "insel", "inseln", "tropical",
			"tropisch", "miami", "malibu", "cali", "california", "hawaii", "bali", "karibik", "caribbean", "paradise", "paradies"},
		{"spotlight", "spotlights", "stage", "stages", "bühne", "show", "shows", "concert", "concerts", "konzert", "konzerte", "crowd", "menge",
			"audience", "publikum", "applause", "applaus", "tour", "touring", "arena", "festival", "perform", "performing", "performance", "auftritt",
			"rampenlicht", "scheinwerfer"},
		{"dumbbell", "gym", "gyms", "workout", "workouts", "lift", "lifting", "pump", "pumper", "hantel", "hanteln", "muscle", "muscles", "muskeln",
			"strong", "stark", "stärker", "fitness", "sweat", "schwitzen", "grind", "grinding", "hustle", "hustling", "sport", "sports"},
	};
	private static final Map<String, Integer> SYMBOL_OF = new HashMap<>();

	static {
		for (int symbol = 0; symbol < SYMBOLS.length; symbol++) {
			for (String word : SYMBOLS[symbol]) {
				SYMBOL_OF.putIfAbsent(word, symbol);
			}
		}
	}

	/** How many symbols there are. */
	static final int SYMBOLS_COUNT = SYMBOLS.length;

	private CutoutLexicon() {
	}

	/** @return a word as the lists know it: lower case, without the marks around it ("Cars," is "cars") */
	static String plain(String word) {
		int from = 0;
		int to = word.length();
		while (from < to && !Character.isLetterOrDigit(word.charAt(from))) {
			from++;
		}
		while (to > from && !Character.isLetterOrDigit(word.charAt(to - 1))) {
			to--;
		}
		return word.substring(from, to).toLowerCase(Locale.ROOT).replace('’', '\'');
	}

	/** Two words that stand for one thing: the symbol goes beside the main word of the two. */
	private static final Map<String, String> PHRASES = Map.ofEntries(Map.entry("private jet", "plane"), Map.entry("first class", "plane"),
		Map.entry("air max", "shoe"), Map.entry("air force", "shoe"), Map.entry("number one", "trophy"), Map.entry("nummer eins", "trophy"),
		Map.entry("new york", "city"), Map.entry("los angeles", "city"), Map.entry("rolls royce", "car"), Map.entry("g wagon", "car"),
		Map.entry("palm tree", "palmtree"), Map.entry("palm trees", "palmtree"), Map.entry("louis vuitton", "hanger"), Map.entry("patek philippe", "wristwatch"),
		Map.entry("record deal", "record"), Map.entry("blue lights", "siren"));
	/**
	 * A word's ending that may be taken off to find it: plurals and verb forms, English and German, the plain s first. What is left keeps at least
	 * {@link #LEAST_STEM} letters ({@link #LEAST_S_STEM} for a plain s), so "cart", "cared" and "barn" are no car or bar; "es" only after a
	 * hissing sound (watches, kisses).
	 */
	private static final String[] ENDINGS = {"s", "es", "ing", "ed", "er", "en", "st", "n", "e", "t"};
	/** The least a word keeps of itself when an ending is taken off, and the least the known end of a compound has. */
	private static final int LEAST_STEM = 4;
	private static final int LEAST_S_STEM = 3;
	private static final int LEAST_COMPOUND_END = 4;

	/** Swear words, English and German, matched as whole plain words (with their forms); slurs are those the lyrics censor ({@link MusicService#SLURS}). */
	private static final Pattern SWEARS = Pattern.compile("(?:motherfuck\\w*|fuck\\w*|shit\\w*|bullshit|bitch\\w*|cunt\\w*|dick(?:s|head\\w*)?|puss(?:y|ies)|"
		+ "asshole\\w*|bastard\\w*|whore\\w*|slut\\w*|scheiß\\w*|scheiss\\w*|fick\\w*|arschloch\\w*|hurensohn\\w*|fotze\\w*|wichser\\w*|schlampe\\w*)",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	/**
	 * @return whether a word is a swear word or a slur, also one censored with asterisks: such a word is never the key word, never on a tile and
	 *         never has a symbol; it stays a plain word at the normal size
	 */
	static boolean rude(String word) {
		if (word.indexOf('*') >= 0) {
			return true;
		}
		String plain = plain(word);
		return !plain.isEmpty() && (SWEARS.matcher(plain).matches() || MusicService.SLURS.matcher(plain).matches());
	}

	/**
	 * @return the symbol that stands beside a word, or {@link #NO_SYMBOL}. Case and marks do not matter; also found: a dropped g (smokin'),
	 *         an 's, an ending taken off (plurals and verb forms), ae, oe and ue for ä, ö and ü, a word joined with a hyphen (Mercedes-Benz) by
	 *         one of its parts, and a German compound (capitalized, as German nouns are) by its known end (Sportwagen)
	 */
	static int symbol(String word) {
		if (rude(word)) {
			return NO_SYMBOL;
		}
		String plain = plain(word);
		Integer found = find(plain);
		if (found == null && plain.indexOf('-') > 0) {
			String[] parts = plain.split("-");
			found = find(String.join("", parts));
			for (int i = parts.length - 1; i >= 0 && found == null; i--) {
				found = find(parts[i]);
			}
		}
		if (found == null && !word.isEmpty() && Character.isUpperCase(word.codePointAt(firstLetter(word)))) {
			found = compoundEnd(plain);
		}
		return found == null ? NO_SYMBOL : found;
	}

	/** @return the symbol two words stand for together ("private jet"), or {@link #NO_SYMBOL} */
	static int phrase(String word, String next) {
		String name = PHRASES.get(plain(word).replace('-', ' ') + " " + plain(next));
		return name == null ? NO_SYMBOL : SYMBOL_OF.getOrDefault(name, NO_SYMBOL);
	}

	private static int firstLetter(String word) {
		int at = 0;
		while (at < word.length() - 1 && !Character.isLetter(word.charAt(at))) {
			at++;
		}
		return at;
	}

	/** @return the symbol of a word as it is, or in one of its other spellings and forms, or null */
	private static Integer find(String plain) {
		if (plain.length() < 2) {
			return null;
		}
		Integer found = spelled(plain);
		if (found == null && plain.endsWith("'s")) {
			found = spelled(plain.substring(0, plain.length() - 2));
		}
		String bare = plain.replace("'", "");
		if (found == null && bare.endsWith("in") && bare.length() > 4) {
			found = spelled(bare + "g");
		}
		for (int i = 0; i < ENDINGS.length && found == null; i++) {
			String stem = bare.substring(0, Math.max(0, bare.length() - ENDINGS[i].length()));
			boolean hissing = stem.endsWith("ch") || stem.endsWith("sh") || stem.endsWith("ss") || stem.endsWith("x") || stem.endsWith("z");
			if (bare.endsWith(ENDINGS[i]) && stem.length() >= (ENDINGS[i].equals("s") ? LEAST_S_STEM : LEAST_STEM) && (!ENDINGS[i].equals("es") || hissing)) {
				found = spelled(stem);
			}
		}
		return found;
	}

	/** @return the symbol of a word, also written with ae, oe and ue for ä, ö and ü, or null */
	private static Integer spelled(String plain) {
		Integer found = SYMBOL_OF.get(plain);
		if (found == null && (plain.contains("ae") || plain.contains("oe") || plain.contains("ue"))) {
			found = SYMBOL_OF.get(plain.replace("ae", "ä").replace("oe", "ö").replace("ue", "ü"));
		}
		return found;
	}

	/** @return the symbol of the longest known end of a compound, as long as what stands before it is a word of some length too; or null */
	private static Integer compoundEnd(String plain) {
		for (int from = LEAST_S_STEM; from <= plain.length() - LEAST_COMPOUND_END; from++) {
			Integer found = spelled(plain.substring(from));
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** @return the words a symbol stands beside */
	static String[] symbolWords(int symbol) {
		return SYMBOLS[symbol].clone();
	}

	/** @return the first word of a symbol's list: its name */
	static String symbolName(int symbol) {
		return SYMBOLS[symbol][0];
	}

	/** @return whether a line is English or German as far as the lists can tell: one of its words is a function word of theirs */
	static boolean known(List<String> words) {
		for (String word : words) {
			String plain = plain(word);
			if (ENGLISH.contains(plain) || GERMAN.contains(plain)) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a word holds a digit: it always says something, however short it is */
	static boolean number(String word) {
		return word.chars().anyMatch(Character::isDigit);
	}

	/** @return whether a word is censored, written with asterisks */
	static boolean censored(String word) {
		return word.indexOf('*') >= 0;
	}

	/**
	 * @param known whether the line is English or German ({@link #known}); in another language a word of one to three letters counts as a
	 *              function word, since most are
	 * @return whether a word is a small function word: an article, a preposition, a pronoun, a conjunction
	 */
	static boolean small(String word, boolean known) {
		if (number(word)) {
			return false;
		}
		String plain = plain(word);
		if (plain.isEmpty()) {
			return true;
		}
		if (ENGLISH.contains(plain) || GERMAN.contains(plain)) {
			return true;
		}
		return !known && plain.codePointCount(0, plain.length()) <= 3 && symbol(plain) == NO_SYMBOL;
	}
}
