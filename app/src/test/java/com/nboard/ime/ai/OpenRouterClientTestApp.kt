import kotlinx.coroutines.runBlocking
import com.nboard.ime.ai.OpenRouterClient
import java.io.File

fun main() {
    val key = System.getenv("OPENROUTER_KEY") ?: ""
    println("Key length: ${key.length}")
    val client = OpenRouterClient(key, "google/gemini-2.5-flash")
    runBlocking {
        val result = client.generateText("Hello, how are you?", "You are a helpful assistant.", 500)
        println("Result: $result")
        if (result.isFailure) {
            println("Error: ${result.exceptionOrNull()?.message}")
            result.exceptionOrNull()?.printStackTrace()
        }
    }
}
