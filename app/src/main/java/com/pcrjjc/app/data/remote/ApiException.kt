package com.pcrjjc.app.data.remote

/**
 * API exception corresponding to ApiException in pcrclient.py
 */
class ApiException(message: String, val code: Int) : Exception(message)
