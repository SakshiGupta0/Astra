package app.astra.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a background calculation: loading, done, or failed with a message (never a crash). */
sealed interface Calc<out T> {
    data object Loading : Calc<Nothing>
    data class Ok<T>(val value: T) : Calc<T>
    data class Failed(val message: String) : Calc<Nothing>
}

val <T> Calc<T>.valueOrNull: T? get() = (this as? Calc.Ok<T>)?.value
val Calc<*>.error: String? get() = (this as? Calc.Failed)?.message

@Composable
fun <T> rememberCalc(key: Any, block: () -> T): State<Calc<T>> = produceState<Calc<T>>(Calc.Loading, key) {
    value = withContext(Dispatchers.Default) {
        try { Calc.Ok(block()) } catch (t: Throwable) { Calc.Failed("${t.javaClass.simpleName}: ${t.message ?: "no details"}") }
    }
}
