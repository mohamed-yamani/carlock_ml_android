import android.content.Context
import android.os.AsyncTask
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadTask(private val context: Context, private val listener: OnDownloadListener) : AsyncTask<String, Int, String>() {

    interface OnDownloadListener {
        fun onProgressUpdate(progress: Int)
        fun onPostExecute(result: String)
    }

    override fun doInBackground(vararg params: String?): String? {
        val modelUrl = params[0]
        val token = params[1]

        try {
            val url = URL(modelUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connect()

            val fileLength = connection.contentLength

            val inputStream: InputStream = connection.inputStream
            val file = File(context.getExternalFilesDir(null), "model22.tflite")
            val fileOutputStream = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var total: Long = 0
            var progress = 0
            var count: Int

            while (inputStream.read(buffer).also { count = it } != -1) {
                total += count.toLong()
                fileOutputStream.write(buffer, 0, count)
                progress = (total * 100 / fileLength).toInt()
                publishProgress(progress)
            }

            fileOutputStream.close()
            inputStream.close()

            return "Success"
        } catch (e: IOException) {
            Log.e("Error: ", e.message!!)
            return "Error"
        }
    }

    override fun onProgressUpdate(vararg values: Int?) {
        super.onProgressUpdate(*values)
        listener.onProgressUpdate(values[0]!!)
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
        listener.onPostExecute(result!!)
    }
}
