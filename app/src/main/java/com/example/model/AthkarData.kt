package com.example.model

data class ThikrItem(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val english: String,
    val targetCount: Int,
    val virtue: String = ""
)

enum class AthkarCategory(val titleEnglish: String, val titleArabic: String) {
    MORNING("Morning Adhkar", "أذكار الصباح"),
    EVENING("Evening Adhkar", "أذكار المساء"),
    AFTER_PRAYER("After Prayer Adhkar", "أذكار بعد الصلاة"),
    SLEEP("Sleeping Adhkar", "أذكار النوم")
}

data class TasbeehPreset(
    val nameArabic: String,
    val nameEnglish: String,
    val target: Int
)

object AthkarRepository {

    val TASBEEH_PRESETS = listOf(
        TasbeehPreset("سبحان الله", "SubhanAllah (Glory be to Allah)", 33),
        TasbeehPreset("الحمد لله", "Alhamdulillah (All praise is due to Allah)", 33),
        TasbeehPreset("الله أكبر", "Allahu Akbar (Allah is the Greatest)", 34),
        TasbeehPreset("أستغفر الله", "Astaghfirullah (I seek forgiveness from Allah)", 100),
        TasbeehPreset("لا إله إلا الله", "La ilaha illa Allah (There is no god but Allah)", 100),
        TasbeehPreset("اللهم صل وسلم على نبينا محمد", "Salawat upon the Prophet (PBUH)", 100),
        TasbeehPreset("لا حول ولا قوة إلا بالله", "La hawla wa la quwwata illa billah", 100),
        TasbeehPreset("سبحان الله وبحمده سبحان الله العظيم", "SubhanAllahi wa bihamdihi", 100)
    )

    fun getAthkar(category: AthkarCategory): List<ThikrItem> {
        return when (category) {
            AthkarCategory.MORNING -> listOf(
                ThikrItem(
                    id = "m_1",
                    arabic = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَـهَ إِلاَّ اللهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                    transliteration = "Asbahna wa-asbahal-mulku lillah, wal-hamdu lillah, la ilaha illallahu wahdahu la shareeka lah, lahul-mulku wa lahul-hamdu, wa huwa 'ala kulli shay'in qadeer.",
                    english = "We have reached the morning and the kingdom belongs to Allah; all praise is due to Allah. There is no deity except Allah alone, without partner; to Him belongs the dominion and to Him belongs all praise, and He is over all things capable.",
                    targetCount = 1,
                    virtue = "Reported in Sahih Muslim"
                ),
                ThikrItem(
                    id = "m_2",
                    arabic = "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ.",
                    transliteration = "Allahumma bika asbahna, wa bika amsayna, wa bika nahya, wa bika namoot, wa ilaykan-nushoor.",
                    english = "O Allah, by You we enter the morning and by You we enter the evening, by You we live and by You we die, and to You is the resurrection.",
                    targetCount = 1,
                    virtue = "Reported by Abu Dawood and At-Tirmidhi"
                ),
                ThikrItem(
                    id = "m_3",
                    arabic = "اللَّهُمَّ أَنْتَ رَبِّي لاَ إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنُوبَ إِلاَّ أَنْتَ.",
                    transliteration = "Allahumma anta Rabbi la ilaha illa anta, khalaqtanee wa ana 'abduka, wa ana 'ala 'ahdika wa wa'dika mastata'tu, a'oodhu bika min sharri ma sana'tu, aboo'u laka bini'matika 'alayya, wa aboo'u bidhanbee faghfir lee fa-innahu la yaghfirudh-dhunooba illa anta.",
                    english = "Sayyid al-Istighfar: O Allah, You are my Lord; there is no god but You. You created me and I am Your servant, and I uphold Your covenant and promise as much as I am able. I seek refuge in You from the evil of what I have done. I acknowledge Your favor upon me, and I acknowledge my sin, so forgive me, for none forgives sins except You.",
                    targetCount = 1,
                    virtue = "Whoever says it with conviction in the morning and dies before evening will be among the people of Paradise (Bukhari)"
                ),
                ThikrItem(
                    id = "m_4",
                    arabic = "بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ العَلِيمُ.",
                    transliteration = "Bismillahil-ladhee la yadurru ma'as-mihi shay'un fil-ardi wa la fis-sama'i wa huwas-Samee'ul-'Aleem.",
                    english = "In the name of Allah, with whose name nothing on earth or in heaven can cause harm, and He is the All-Hearing, the All-Knowing.",
                    targetCount = 3,
                    virtue = "Whoever recites it three times will not be harmed by anything (At-Tirmidhi)"
                ),
                ThikrItem(
                    id = "m_5",
                    arabic = "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالإِسْلاَمِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا.",
                    transliteration = "Radeetu billahi Rabban, wa bil-Islami deenan, wa bi-Muhammadin sallallahu 'alayhi wa sallama Nabiyya.",
                    english = "I am pleased with Allah as my Lord, with Islam as my religion, and with Muhammad (peace and blessings of Allah be upon him) as my Prophet.",
                    targetCount = 3,
                    virtue = "Allah has promised to please the one who says it three times (Abu Dawood)"
                ),
                ThikrItem(
                    id = "m_6",
                    arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ.",
                    transliteration = "SubhanAllahi wa bihamdihi: 'adada khalqihi, wa rida nafsihi, wa zinata 'arshihi, wa midada kalimatih.",
                    english = "Glory is to Allah and praise is to Him: by the number of His creation, by His pleasure, by the weight of His Throne, and by the ink of His words.",
                    targetCount = 3,
                    virtue = "Outweighs hours of regular glorification (Muslim)"
                ),
                ThikrItem(
                    id = "m_7",
                    arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.",
                    transliteration = "SubhanAllahi wa bihamdih.",
                    english = "Glory is to Allah and all praise is to Him.",
                    targetCount = 100,
                    virtue = "His sins will be forgiven even if they are like the foam of the sea (Bukhari)"
                )
            )

            AthkarCategory.EVENING -> listOf(
                ThikrItem(
                    id = "e_1",
                    arabic = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لاَ إِلَـهَ إِلاَّ اللهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                    transliteration = "Amsayna wa-amsal-mulku lillah, wal-hamdu lillah, la ilaha illallahu wahdahu la shareeka lah, lahul-mulku wa lahul-hamdu, wa huwa 'ala kulli shay'in qadeer.",
                    english = "We have reached the evening and the dominion belongs to Allah; all praise is due to Allah. There is no deity except Allah alone, without partner; to Him belongs the dominion and to Him belongs all praise.",
                    targetCount = 1,
                    virtue = "Reported in Sahih Muslim"
                ),
                ThikrItem(
                    id = "e_2",
                    arabic = "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ.",
                    transliteration = "Allahumma bika amsayna, wa bika asbahna, wa bika nahya, wa bika namoot, wa ilaykal-maseer.",
                    english = "O Allah, by You we enter the evening and by You we enter the morning, by You we live and by You we die, and to You is the final return.",
                    targetCount = 1,
                    virtue = "Reported by Abu Dawood and At-Tirmidhi"
                ),
                ThikrItem(
                    id = "e_3",
                    arabic = "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.",
                    transliteration = "A'oodhu bikalimatil-lahit-tammati min sharri ma khalaq.",
                    english = "I seek refuge in the perfect words of Allah from the evil of what He has created.",
                    targetCount = 3,
                    virtue = "No poison or harmful creature shall harm him that night (Muslim)"
                ),
                ThikrItem(
                    id = "e_4",
                    arabic = "بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ العَلِيمُ.",
                    transliteration = "Bismillahil-ladhee la yadurru ma'as-mihi shay'un fil-ardi wa la fis-sama'i wa huwas-Samee'ul-'Aleem.",
                    english = "In the name of Allah, with whose name nothing on earth or in heaven can cause harm, and He is the All-Hearing, the All-Knowing.",
                    targetCount = 3,
                    virtue = "Protects from all harm through the night"
                )
            )

            AthkarCategory.AFTER_PRAYER -> listOf(
                ThikrItem(
                    id = "p_1",
                    arabic = "أَسْتَغْفِرُ اللهَ، أَسْتَغْفِرُ اللهَ، أَسْتَغْفِرُ اللهَ. اللَّهُمَّ أَنْتَ السَّلاَمُ وَمِنْكَ السَّلاَمُ، تَبَارَكْتَ يَا ذَا الْجَلاَلِ وَالإِكْرَامِ.",
                    transliteration = "Astaghfirullah (3x). Allahumma antas-Salam wa minkas-Salam, tabarakta ya dhal-Jalali wal-Ikram.",
                    english = "I ask Allah for forgiveness (3 times). O Allah, You are Peace and from You comes peace. Blessed are You, O Owner of majesty and honor.",
                    targetCount = 1,
                    virtue = "Sunnah immediately following obligatory prayer (Muslim)"
                ),
                ThikrItem(
                    id = "p_2",
                    arabic = "سُبْحَانَ اللَّهِ (٣٣)، الْحَمْدُ لِلَّهِ (٣٣)، اللَّهُ أَكْبَرُ (٣٣)، لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                    transliteration = "SubhanAllah (33x), Alhamdulillah (33x), Allahu Akbar (33x), La ilaha illallahu wahdahu la shareeka lah, lahul-mulku wa lahul-hamdu, wa huwa 'ala kulli shay'in qadeer.",
                    english = "Glory be to Allah (33), Praise be to Allah (33), Allah is the Greatest (33), and to seal a hundred: There is no deity except Allah alone without partner.",
                    targetCount = 1,
                    virtue = "Sins will be forgiven even if like the foam of the sea (Muslim)"
                ),
                ThikrItem(
                    id = "p_3",
                    arabic = "اللَّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ لاَ تَأْخُذُهُ سِنَةٌ وَلاَ نَوْمٌ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الأَرْضِ مَن ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلاَّ بِإِذْنِهِ...",
                    transliteration = "Ayat al-Kursi (Surah Al-Baqarah 2:255)",
                    english = "Allah - there is no deity except Him, the Ever-Living, the Sustainer of all existence...",
                    targetCount = 1,
                    virtue = "Whoever recites it after every prayer, nothing prevents him from entering Paradise except death (An-Nasa'i)"
                )
            )

            AthkarCategory.SLEEP -> listOf(
                ThikrItem(
                    id = "s_1",
                    arabic = "بِاسْمِكَ رَبِّي وَضَعْتُ جَنْبِي، وَبِكَ أَرْفَعُهُ، فَإِن أَمْسَكْتَ نَفْسِي فَارْحَمْهَا، وَإِنْ أَرْسَلْتَهَا فَاحْفَظْهَا بِمَا تَحْفَظُ بِهِ عِبَادَكَ الصَّالِحِينَ.",
                    transliteration = "Bismika Rabbee wada'tu janbee, wa bika arfa'uh, fa-in amsakta nafsee farhamha, wa-in arsaltaha fahfadh-ha bima tahfadhu bihi 'ibadakas-saliheen.",
                    english = "In Your name my Lord, I lie down, and in Your name I rise. If You take my soul, have mercy upon it, and if You release it, protect it as You protect Your righteous servants.",
                    targetCount = 1,
                    virtue = "Reported by Bukhari and Muslim"
                ),
                ThikrItem(
                    id = "s_2",
                    arabic = "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ.",
                    transliteration = "Allahumma qinee 'adhabaka yawma tab'athu 'ibadak.",
                    english = "O Allah, save me from Your punishment on the Day You resurrect Your servants.",
                    targetCount = 3,
                    virtue = "Recited by the Prophet (PBUH) placing his right hand under his cheek (Abu Dawood)"
                )
            )
        }
    }
}
