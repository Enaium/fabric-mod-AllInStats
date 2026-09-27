/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.allinstats.db

import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * A minimal [DataSource] on top of [DriverManager]. The H2 driver is provided by the
 * `fabric-database-h2` mod, which is why no H2 class is referenced directly.
 *
 * @author Enaium
 */
class SimpleDataSource(
    val url: String,
    val username: String? = null,
    val password: String? = null,
) : DataSource {
    override fun getConnection(): Connection = DriverManager.getConnection(url, username, password)

    override fun getConnection(username: String?, password: String?): Connection =
        DriverManager.getConnection(url, username, password)

    override fun getLogWriter(): PrintWriter = throw UnsupportedOperationException()

    override fun setLogWriter(out: PrintWriter?) = throw UnsupportedOperationException()

    override fun setLoginTimeout(seconds: Int) = throw UnsupportedOperationException()

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): Logger = throw UnsupportedOperationException()

    override fun <T : Any?> unwrap(iface: Class<T?>?): T = throw UnsupportedOperationException()

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
