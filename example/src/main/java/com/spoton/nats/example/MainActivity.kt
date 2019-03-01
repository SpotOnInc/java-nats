package com.spoton.nats.example

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.nats.client.Connection
import io.nats.client.Consumer
import io.nats.client.ErrorListener
import io.nats.client.Options
import io.nats.client.impl.NatsImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    lateinit var job: Job

    //threadpool dispatcher and the service lifecycle linked job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    //NATS connection
    private lateinit var connection : Connection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        job = Job()

        launch {
            Options.Builder()
                    .server("http://myserver.com")
                    .userInfo("user", "password")
                    .connectionListener { conn, type ->
                        Timber.d("ConnectionEvent: $type  -> status: ${conn.status}")
                        connection = conn
                    }
                    .errorListener( object : ErrorListener {
                        override fun errorOccurred(conn: Connection?, error: String?) {
                            Timber.e("Nats error occurred $error")
                        }

                        override fun exceptionOccurred(conn: Connection?, exp: java.lang.Exception?) {
                            Timber.e(exp, "NATS Exception")
                        }

                        override fun slowConsumerDetected(conn: Connection?, consumer: Consumer?) {
                            Timber.w("Slow consumer $consumer")
                        }
                    } )
                    .build().let {
                        try {
                            NatsImpl.createConnection(it, true)
                        } catch (ex: Exception) {
                            Timber.e(ex, "error in NATS connection")
                            it.errorListener?.exceptionOccurred(null, ex)
                        }
                    }
        }
    }

    override fun onDestroy() {
        job.cancel()
        connection.close()
        super.onDestroy()
    }
}
