package com.sundbybergsit.cromfortune.ui.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.sundbybergsit.cromfortune.R

class ToDoDialogFragment : DialogFragment() {

    @SuppressLint("SetTextI18n")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = "Att göra:\n" +
                "1. Hem: Senaste kurs\n" +
                "2. Hem: Mute/unmute aktie\n" +
                "3. Inställningar: Ställ in tidsintervall då kurser ska hämtas\n" +
                "4. Notifieringar: Lägg till arkiv\n" +
                "5. Notiferingar: Lägg till humpnotiser då en kurs är lägre än senaste sälj (vid 0 aktier)\n" +
                "6. Dark theme vid vissa klockslag\n" +
                "7. Hem: Implementera köp/sälj-knappar\n" +
                "8. Hem: Sortering (bäst/sämst/ABC)\n" +
                "9. Inställningar: Lägg till alarm\n" +
                "10. Hem: Fixa refresh-bugg för när man har tagit bort en transaktion (byt tabb och tillbaka så länge)\n" +
                "11. Inställningar: Implementera stöd för individuell courtageplan\n" +
                "12. Instrumentbräda: Exkludera transaktioner från 0 -> x aktier från Crom conformance score\n"
        val context = requireContext()
        return AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(getText(R.string.action_close)) { _, _ -> }
                .create()
    }

}