package com.github.rabilrbl.jiotvgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.rabilrbl.jiotvgo.ui.theme.JioTVGoTheme
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JioTVGoApp()
        }
    }

    suspend fun downloadAndExecuteBinary(
        githubOwner: String,
        repoName: String,
        cliArgs: String,
        outputText: MutableState<String>
    ) {
        val client = OkHttpClient()
        val githubApiUrl = "https://api.github.com/repos/$githubOwner/$repoName/releases/latest"

        try {
            // Fetch release details
            val request = Request.Builder().url(githubApiUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                outputText.value = "Error fetching release information: ${response.message}"
                return
            }

            val releaseJson = response.body?.string() ?: ""
            val downloadUrl = getDownloadUrlFromJson(releaseJson) ?: ""

            if (downloadUrl.isEmpty()) {
                outputText.value = "Could not find binary download URL in release"
                return
            }

            // Download the binary
            val binaryFile = downloadBinary(downloadUrl)

            // Make it executable
            binaryFile?.setExecutable(true)

            // Execute
            executeBinary(binaryFile, cliArgs, outputText)


        } catch (e: IOException) {
            outputText.value = "Network error: ${e.message}"
        }
    }

    private fun getDownloadUrlFromJson(json: String): String? {
        // TODO: Use a JSON parsing library (Gson, kotlinx.serialization, Jackson) to extract the download URL
        //       Example assuming a 'browser_download_url' field in the JSON response
        return "https://example.com/binary.zip" // Replace with your JSON parsing logic
    }

    private suspend fun downloadBinary(url: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) return@withContext null

        val bytes = response.body?.bytes() ?: return@withContext null
        val file = File(filesDir, "downloaded_binary")
        file.writeBytes(bytes)
        return@withContext file
    }

    private fun executeBinary(binaryFile: File?, cliArgs: String, outputText: MutableState<String>) {
        if (binaryFile == null) {
            outputText.value = "Binary file not found"
            return
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf(binaryFile.absolutePath, *cliArgs.split(" ").toTypedArray()))
            val reader = process.inputStream.bufferedReader()
            outputText.value = reader.readText()
        } catch (e: IOException) {
            outputText.value = "Error executing binary: ${e.message}"
        }
    }
}

@Composable
fun JioTVGoApp() {
    var cliArgs by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("GitHub Binary Downloader", style = MaterialTheme.typography.headlineSmall)
        TextField(
            value = cliArgs,
            onValueChange = { cliArgs = it },
            label = { Text("CLI Arguments") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                isLoading = true
                outputText = ""
                val mainActivity = MainActivity()
                CoroutineScope(Dispatchers.IO).launch {
                    mainActivity.downloadAndExecuteBinary(
                        githubOwner = "rabilrbl",
                        repoName = "jiotv_go",
                        cliArgs = cliArgs,
                        outputText = mutableStateOf(outputText)
                    )
                }
                isLoading = false
            },
            enabled = !isLoading
        ) {
            Text("Download and Execute")
        }
        if (isLoading) {
            CircularProgressIndicator()
        }
        Text(outputText)
    }
}
