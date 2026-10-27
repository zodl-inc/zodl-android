package com.flexa.core.entity.error

class SDKInitializationError :
    Exception("Flexa SDK hasn't been initialized. You forgot to use Flexa.init(...) method")
