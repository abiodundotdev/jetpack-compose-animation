package com.abiodun.composeanimation.course.lessons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty

interface Logger {
   fun log(): Unit
   fun name(): String
}

class ConsoleLogger : Logger {
    override fun log() = println("I am the new original logger, how about you")
    override fun name(): String = "Console"
}
//Class delegation
class Manager(logger: Logger): Logger by logger {
    fun logUser() {
        log()
    }
}

class Delegator {
    private var value = ""
    operator fun getValue(thisRef: Any?, prop: KProperty<*>) = value
    operator fun setValue(thisRef: Any?, prop: KProperty<*>, v: String) {
        value = v.trim()
    }
}

fun<T> typeParam(block : () -> T) :  T {
    return block()
}


fun mainApp() {
    val manager = Manager(ConsoleLogger())
    manager.log()
    val delegator : String  by Delegator()
    val name : String = typeParam {
        ""
    }

    val listValues = listOf<Pair<String, String>>(
        "name" to "Qazeem",
        "age" to "89"
    )

    listValues.forEach {
        println(it.first)
    }
}

@Composable
fun ViewA() {
    var name by remember { mutableStateOf("") };

}