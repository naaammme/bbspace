package com.naaammme.bbspace.core.common.dispatcher

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: BbspaceDispatchers)

enum class BbspaceDispatchers {
    IO,
    Default,
    Main
}
