package com.am24.imbrel.presentation

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.am24.imbrel.R

object DialogManager {
    fun locationSettingsDialog(
        context: Context,
        listener: Listener,
    ) {
        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()

        dialog.setTitle(R.string.enable_location)
        dialog.setMessage(context.getString(R.string.want_enable_location))

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { _, _ ->
            listener.onClick(null) // Передаємо null, бо ми просто підтвердили дію
            dialog.dismiss() // Закриваємо вікно
        }

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.cancel)) { _, _ ->
            dialog.dismiss()
        }

        dialog.show()
    }

    interface Listener {
        fun onClick(name: String?)
    }
}
