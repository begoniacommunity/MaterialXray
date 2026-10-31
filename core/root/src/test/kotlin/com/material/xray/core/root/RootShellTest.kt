package com.material.xray.core.root

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootShellTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `fresh access checks detect revocation and restoration without closing the working shell`() = runBlocking {
        File(folder.root, "id").apply {
            writeText("#!/bin/sh\nprintf '0\\n'\n")
            setExecutable(true)
        }
        File(folder.root, "readlink").apply {
            writeText("#!/bin/sh\nprintf 'net:[41]\\n'\n")
            setExecutable(true)
        }
        val denied = AtomicBoolean(false)
        val processes = AtomicInteger()
        val shell = RootShell(
            appProcessId = 123,
            startProcess = {
                processes.incrementAndGet()
                if (denied.get()) {
                    ProcessBuilder("sh", "-c", "exit 1").start()
                } else {
                    ProcessBuilder("sh").apply {
                        environment()["PATH"] = "${folder.root}:${System.getenv("PATH")}"
                    }.start()
                }
            },
        )
        try {
            assertTrue(shell.open(RootShell.NetworkNamespace.INIT))
            denied.set(true)

            assertFalse(shell.checkAccess(RootShell.NetworkNamespace.INIT))
            assertEquals("still running", shell.execute("printf 'still running'").output)
            denied.set(false)

            assertTrue(shell.checkAccess(RootShell.NetworkNamespace.INIT))
            assertEquals(3, processes.get())
            assertTrue(shell.execute("true").isSuccess)
        } finally {
            shell.close()
        }
    }

    @Test
    fun `unqualified root commands default to the init namespace`() {
        assertEquals(
            RootShell.NetworkNamespace.INIT,
            RootShell(appProcessId = 123).defaultNetworkNamespace(),
        )
    }

    @Test
    fun `namespace identity can map directly to app init or both`() {
        assertEquals(
            setOf(RootShell.NetworkNamespace.CURRENT),
            detectDirectRootShellNamespaces("net:[41]", "net:[41]", "net:[42]"),
        )
        assertEquals(
            setOf(RootShell.NetworkNamespace.INIT),
            detectDirectRootShellNamespaces("net:[42]", "net:[41]", "net:[42]"),
        )
        assertEquals(
            setOf(RootShell.NetworkNamespace.CURRENT, RootShell.NetworkNamespace.INIT),
            detectDirectRootShellNamespaces("net:[42]", "net:[42]", "net:[42]"),
        )
    }

    @Test
    fun `missing or third-party namespace IDs remain indirect`() {
        assertEquals(
            emptySet<RootShell.NetworkNamespace>(),
            detectDirectRootShellNamespaces("net:[40]", "net:[41]", "net:[42]"),
        )
        assertEquals(
            emptySet<RootShell.NetworkNamespace>(),
            detectDirectRootShellNamespaces(null, "net:[41]", "net:[42]"),
        )
    }

    @Test
    fun `tagged namespace probes preserve missing values`() {
        assertEquals(
            mapOf("shell" to "net:[40]", "app" to "", "init" to "net:[42]"),
            parseTaggedRootValues("shell=net:[40]\napp=\ninit=net:[42]"),
        )
    }

    @Test
    fun `commands cannot change or end the persistent shell`() = runBlocking {
        File(folder.root, "id").apply {
            writeText("#!/bin/sh\nprintf '0\\n'\n")
            setExecutable(true)
        }
        File(folder.root, "readlink").apply {
            writeText("#!/bin/sh\nprintf 'net:[41]\\n'\n")
            setExecutable(true)
        }
        val shell = RootShell(
            appProcessId = 123,
            startProcess = {
                ProcessBuilder("sh").apply {
                    environment()["PATH"] = "${folder.root}:${System.getenv("PATH")}"
                }.start()
            },
        )
        try {
            assertTrue(shell.open(RootShell.NetworkNamespace.INIT))
            val exited = shell.execute("echo partial; echo oops >&2; exit 3")
            assertEquals(3, exited.exitCode)
            assertEquals("partial", exited.output.trim())
            assertEquals("oops", exited.error)
            assertFalse(shell.execute("if then").isSuccess)
            assertTrue(shell.execute("cd /; marker=set").isSuccess)
            assertEquals("${System.getProperty("user.dir")}|", shell.execute("echo \"\$PWD|\$marker\"").output.trim())
            assertEquals("no newline", shell.execute("printf 'no newline'").output.trim())
        } finally {
            shell.close()
        }
    }

    @Test
    fun `same-namespace commands retain an isolated child shell`() {
        assertEquals(
            "( eval 'ip rule show' )",
            wrapRootCommand(
                command = "ip rule show",
                requestedNamespace = RootShell.NetworkNamespace.INIT,
                directNamespaces = setOf(RootShell.NetworkNamespace.INIT),
                appProcessId = 123,
            ),
        )
    }

    @Test
    fun `cross-namespace commands target init or app process`() {
        assertEquals(
            "nsenter -t 1 -n -- sh -c 'printf '\\''init'\\'''",
            wrapRootCommand(
                command = "printf 'init'",
                requestedNamespace = RootShell.NetworkNamespace.INIT,
                directNamespaces = setOf(RootShell.NetworkNamespace.CURRENT),
                appProcessId = 123,
            ),
        )
        assertEquals(
            "nsenter -t 123 -n -- sh -c 'ip link show'",
            wrapRootCommand(
                command = "ip link show",
                requestedNamespace = RootShell.NetworkNamespace.CURRENT,
                directNamespaces = setOf(RootShell.NetworkNamespace.INIT),
                appProcessId = 123,
            ),
        )
    }
}
