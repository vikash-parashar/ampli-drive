package com.vikash.ampli.drive.voice

import androidx.annotation.StringRes
import com.vikash.ampli.drive.R

/**
 * Settings › Voice › What can I say?: what "Hey Ampli" understands, with examples in English,
 * Hinglish and Hindi. The examples are what drivers actually say, so they are not translated;
 * only the group titles are. VoiceCommandCatalogTest checks that every example parses.
 */
object VoiceCommandCatalog {

    /** [examples]: say any one. [followUp]: only right after another command ("aur" after a volume change). */
    data class Item(val examples: List<String>, val followUp: Boolean = false)

    data class Group(@StringRes val title: Int, val items: List<Item>)

    private fun item(vararg examples: String) = Item(examples.toList())

    val GROUPS: List<Group> = listOf(
        Group(
            R.string.voice_cmds_music,
            listOf(
                item("Play", "Gaana chalao", "गाना बजाओ"),
                item("Pause", "Gaana band karo", "गाना रोको"),
                item("Stop", "Ruko", "बंद करो"),
                item("Play next song", "Agla gaana", "अगला गाना"),
                item("Previous", "Pichla gaana", "पिछला गाना"),
                item("Play Kesariya", "Kesariya bajao", "केसरिया लगाओ"),
                item("Play music from my phone", "Phone ke gaane bajao"),
            ),
        ),
        Group(
            R.string.voice_cmds_volume,
            listOf(
                item("Volume up", "Awaaz badhao", "आवाज़ बढ़ाओ"),
                item("Volume down", "Awaaz kam karo", "आवाज़ कम करो"),
                item("Volume full", "Volume 5", "आवाज़ पाँच कर दो"),
                item("Mute", "Unmute", "आवाज़ बंद"),
                Item(listOf("Aur", "More", "और"), followUp = true),
            ),
        ),
        Group(
            R.string.voice_cmds_info,
            listOf(
                item("What is the time?", "Kitne baje hain?", "टाइम क्या हुआ?"),
                item("What's the date?", "Aaj kya date hai?", "आज कौन सा दिन है?"),
                item("How is the weather?", "Mausam kaisa hai?", "मौसम कैसा है?"),
                item("Will it rain?", "Baarish hogi kya?", "बारिश होगी?"),
                item("What's my speed?", "Speed kitni hai?", "स्पीड कितनी है?"),
            ),
        ),
        Group(
            R.string.voice_cmds_nav,
            listOf(
                item("Take me to Phoenix Mall", "Rohtak le chalo", "सोनीपत ले चल"),
                item("Go home", "Ghar chalo", "Office chalo"),
                item("Stop navigation", "Navigation band karo", "रास्ता बंद करो"),
                item("Open maps", "Map kholo", "नक्शा खोलो"),
                item("Next speed camera", "Agla camera", "अगला कैमरा"),
            ),
        ),
        Group(
            R.string.voice_cmds_fuel,
            listOf(
                item("Nearest CNG pump", "CNG pump kahan hai", "सीएनजी पंप कहाँ है"),
                item("Petrol pump", "Diesel pump", "Charging station"),
                item("Police aage hai", "Accident ahead", "आगे जाम है"),
                item("Mobile camera ahead", "मोबाइल कैमरा आगे है"),
            ),
        ),
        Group(
            R.string.voice_cmds_car,
            listOf(
                item("Where is my car?", "Gaadi kahan hai?", "गाड़ी कहाँ खड़ी है?"),
                item("Next service", "Tyre pressure", "Engine status"),
            ),
        ),
        Group(
            R.string.voice_cmds_phone,
            listOf(
                item("Call Papa", "Papa ko phone karo", "मम्मी को फोन करो"),
                item("Connect my phone", "Phone jodo", "Android Auto"),
                item("Bluetooth settings", "Bluetooth kholo", "ब्लूटूथ खोलो"),
                item("Brightness up", "Brightness kam karo", "ब्राइटनेस बढ़ाओ"),
                item("Save the video", "Save kar", "क्लिप सेव कर दे"),
            ),
        ),
        Group(
            R.string.voice_cmds_help,
            listOf(
                item("SOS", "Madad", "बचाओ"),
                item("Report a problem", "शिकायत"),
                item("What can I say?", "Kya kya bol sakte hain?", "Cancel"),
                item("How are you?", "Tell me a joke", "चुटकुला सुनाओ"),
            ),
        ),
    )
}
