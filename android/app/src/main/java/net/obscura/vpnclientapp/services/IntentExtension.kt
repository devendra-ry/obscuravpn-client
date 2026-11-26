package net.obscura.vpnclientapp.services

import android.content.Intent
import net.obscura.vpnclientapp.BuildConfig

private const val EXTRA_EXIT_SELECTOR = "exitSelector"
const val ACTION_START_TUNNEL = "${BuildConfig.APPLICATION_ID}.actionStartTunnel"
const val ACTION_STOP_TUNNEL = "${BuildConfig.APPLICATION_ID}.actionStopTunnel"

fun Intent.putStartTunnelExtras(exitSelector: String?) = this.putExtra(EXTRA_EXIT_SELECTOR, exitSelector)

fun Intent.getStartTunnelExtras() = this.getStringExtra(EXTRA_EXIT_SELECTOR)
