// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.
package example.simple

import com.jetbrains.cef.JCefAppConfig
import org.cef.CefApp
import org.cef.CefApp.CefAppState
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefAppHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefFocusHandlerAdapter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.IOException
import java.util.*
import javax.swing.JFrame
import javax.swing.JTextField

/**
 * This is a simple example application using JCEF.
 * It displays a JFrame with a JTextField at its top and a CefBrowser in its
 * center. The JTextField is used to enter and assign an URL to the browser UI.
 * No additional handlers or callbacks are used in this example.
 *
 * The number of used JCEF classes is reduced (nearly) to its minimum and should
 * assist you to get familiar with JCEF.
 *
 * For a more feature complete example have also a look onto the example code
 * within the package "tests.detailed".
 */
class MainFrame constructor(args: Array<String>, startURL: String, useOSR: Boolean, isTransparent: Boolean) :
    JFrame() {
    private val address_: JTextField
    private val cefApp_: CefApp
    private val client_: CefClient
    private val browser_: CefBrowser
    private val browerUI_: Component
    private var browserFocus_ = true

    companion object {
        private const val serialVersionUID = -5570653778104813836L
        private fun normalize(path: String): String {
            return try {
                File(path).canonicalPath
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            // Perform startup initialization on platforms that require it.
            if (!CefApp.startup(args)) {
                println("Startup initialization failed!")
                return
            }

            // The simple example application is created as anonymous class and points
            // to Google as the very first loaded page. Windowed rendering mode is used by
            // default. If you want to test OSR mode set |useOsr| to true and recompile.
            val useOsr = false
            MainFrame(args, "http://www.google.com", useOsr, false)
        }
    }

    /**
     * To display a simple browser window, it suffices completely to create an
     * instance of the class CefBrowser and to assign its UI component to your
     * application (e.g. to your content pane).
     * But to be more verbose, this CTOR keeps an instance of each object on the
     * way to the browser UI.
     */
    init {
        var args = args
        val config = JCefAppConfig.getInstance()
        val appArgs: MutableList<String> = ArrayList(Arrays.asList(*args))
        appArgs.addAll(config.appArgsAsList)
        args = appArgs.toTypedArray()

        // (1) The entry point to JCEF is always the class CefApp. There is only one
        //     instance per application and therefore you have to call the method
        //     "getInstance()" instead of a CTOR.
        //
        //     CefApp is responsible for the global CEF context. It loads all
        //     required native libraries, initializes CEF accordingly, starts a
        //     background task to handle CEF's message loop and takes care of
        //     shutting down CEF after disposing it.
        CefApp.addAppHandler(object : CefAppHandlerAdapter(args) {
            override fun stateHasChanged(state: CefAppState) {
                // Shutdown the app if the native CEF part is terminated
                if (state == CefAppState.TERMINATED) System.exit(0)
            }
        })
        val settings = config.cefSettings
        settings.windowless_rendering_enabled = useOSR
        cefApp_ = CefApp.getInstance(settings)

        // (2) JCEF can handle one to many browser instances simultaneous. These
        //     browser instances are logically grouped together by an instance of
        //     the class CefClient. In your application you can create one to many
        //     instances of CefClient with one to many CefBrowser instances per
        //     client. To get an instance of CefClient you have to use the method
        //     "createClient()" of your CefApp instance. Calling an CTOR of
        //     CefClient is not supported.
        //
        //     CefClient is a connector to all possible events which come from the
        //     CefBrowser instances. Those events could be simple things like the
        //     change of the browser title or more complex ones like context menu
        //     events. By assigning handlers to CefClient you can control the
        //     behavior of the browser. See tests.detailed.MainFrame for an example
        //     of how to use these handlers.
        client_ = cefApp_.createClient()

        // (3) One CefBrowser instance is responsible to control what you'll see on
        //     the UI component of the instance. It can be displayed off-screen
        //     rendered or windowed rendered. To get an instance of CefBrowser you
        //     have to call the method "createBrowser()" of your CefClient
        //     instances.
        //
        //     CefBrowser has methods like "goBack()", "goForward()", "loadURL()",
        //     and many more which are used to control the behavior of the displayed
        //     content. The UI is held within a UI-Compontent which can be accessed
        //     by calling the method "getUIComponent()" on the instance of CefBrowser.
        //     The UI component is inherited from a java.awt.Component and therefore
        //     it can be embedded into any AWT UI.
        browser_ = client_.createBrowser(startURL, useOSR, isTransparent)
        browerUI_ = browser_.uiComponent

        // (4) For this minimal browser, we need only a text field to enter an URL
        //     we want to navigate to and a CefBrowser window to display the content
        //     of the URL. To respond to the input of the user, we're registering an
        //     anonymous ActionListener. This listener is performed each time the
        //     user presses the "ENTER" key within the address field.
        //     If this happens, the entered value is passed to the CefBrowser
        //     instance to be loaded as URL.
        address_ = JTextField(startURL, 100)
        address_.addActionListener { browser_.loadURL(address_.text) }

        // Update the address field when the browser URL changes.
        client_.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                address_.text = url
            }
        })

        // Clear focus from the browser when the address field gains focus.
        address_.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                if (!browserFocus_) return
                browserFocus_ = false
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner()
                address_.requestFocus()
            }
        })

        // Clear focus from the address field when the browser gains focus.
        client_.addFocusHandler(object : CefFocusHandlerAdapter() {
            override fun onGotFocus(browser: CefBrowser) {
                if (browserFocus_) return
                browserFocus_ = true
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner()
                browser.setFocus(true)
            }

            override fun onTakeFocus(browser: CefBrowser, next: Boolean) {
                browserFocus_ = false
            }
        })

        // (5) All UI components are assigned to the default content pane of this
        //     JFrame and afterwards the frame is made visible to the user.
        contentPane.add(address_, BorderLayout.NORTH)
        contentPane.add(browerUI_, BorderLayout.CENTER)
        pack()
        setSize(800, 600)
        isVisible = true

        // (6) To take care of shutting down CEF accordingly, it's important to call
        //     the method "dispose()" of the CefApp instance if the Java
        //     application will be closed. Otherwise you'll get asserts from CEF.
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                CefApp.getInstance().dispose()
                dispose()
            }
        })
    }

    fun getClient(): CefClient {
        return client_
    }
}