package com.tukorea.blackletters.network

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

object SshManager {
    private const val HOST = "43.202.24.80"
    private const val USER = "ec2-user"
    private const val KEY_NAME = "b_l_team.pem"

    suspend fun runBatchQuery(context: Context) = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var session: Session? = null
        
        try {
            // Assets에서 PEM 키 읽기
            val keyInputStream = context.assets.open(KEY_NAME)
            val keyBytes = keyInputStream.readBytes()
            keyInputStream.close()

            // 키 등록
            jsch.addIdentity("BL_KEY", keyBytes, null, null)

            session = jsch.getSession(USER, HOST, 22)
            
            val config = Properties()
            config["StrictHostKeyChecking"] = "no" // 처음 접속 시 yes 입력 방지
            session.setConfig(config)

            session.connect(10000) // 10초 타임아웃

            val command = "docker exec -i blackletters-mysql mysql -u root -pblackletters1234 --default-character-set=utf8mb4 receipt_app < monthly_summary_batch.sql"
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.inputStream = null
            channel.setErrStream(System.err)

            val input: InputStream = channel.inputStream
            channel.connect()

            // 결과 읽기 (로그용)
            val buffer = ByteArray(1024)
            while (true) {
                while (input.available() > 0) {
                    val i = input.read(buffer, 0, 1024)
                    if (i < 0) break
                    Log.d("SSH", String(buffer, 0, i))
                }
                if (channel.isClosed) {
                    if (input.available() > 0) continue
                    Log.d("SSH", "exit-status: " + channel.exitStatus)
                    break
                }
                Thread.sleep(1000)
            }
            channel.disconnect()
            Log.d("SSH", "Batch Query Executed Successfully")
        } catch (e: Exception) {
            Log.e("SSH", "Error executing SSH command", e)
        } finally {
            session?.disconnect()
        }
    }
}
