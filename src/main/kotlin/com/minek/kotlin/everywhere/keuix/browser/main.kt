package com.minek.kotlin.everywhere.keuix.browser

import com.minek.kotlin.everywhere.keuix.browser.Snabbdom.h
import com.minek.kotlin.everywhere.keuix.browser.debugger.debugger
import com.minek.kotlin.everywhere.keuix.browser.html.Attribute
import com.minek.kotlin.everywhere.keuix.browser.html.Html
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import kotlin.browser.window
import com.minek.kotlin.everywhere.keuix.browser.debugger.Model as DebuggerModel
import com.minek.kotlin.everywhere.keuix.browser.debugger.Msg as DebuggerMsg

data class AttributeSet(val props: dynamic, val attrs: dynamic, val on: dynamic, val key: String?)

private fun <S> List<Attribute<S>>.toProps(receiver: (S) -> Unit): AttributeSet {
    val props: dynamic = object {}
    val attrs: dynamic = object {}
    val on: dynamic = object {}
    var key: String? = null

    val eventMap = mutableMapOf<String, MutableList<(Event) -> S?>>()
    this.forEach { attr ->
        when (attr) {
            is Attribute.TextProperty -> props[attr.name] = attr.value
            is Attribute.BooleanProperty -> props[attr.name] = attr.value
            is Attribute.TextAttribute -> attrs[attr.name] = attr.value
            is Attribute.BooleanAttribute -> attrs[attr.name] = attr.value
            is Attribute.Key -> key = attr.key
            is Attribute.EventHandler -> {
                eventMap.getOrPut(attr.name, ::mutableListOf).add(attr.value)
            }
        }
    }
    eventMap.forEach { (name, handlers) ->
        on[name] = { event: Event ->
            handlers.forEach {
                it(event)?.let(receiver)
            }
        }
    }

    return AttributeSet(props, attrs, on, key)
}

private fun <S, P> tagVirtualNode(tagger: Html.Tagger<S, P>, receiver: (S) -> Unit): dynamic {
    return tagger.html.toVirtualNode { p -> receiver(tagger.tagger(p)) }
}

private fun <S> Html<S>.toVirtualNode(receiver: (S) -> Unit): dynamic {
    return when (this) {
        is Html.Text -> this.text
        is Html.Element -> {
            val data: dynamic = object {}
            val (props, attrs, on, key) = this.attributes.toProps(receiver)
            data["props"] = props
            data["attrs"] = attrs
            data["on"] = on
            if (key != null) {
                data["key"] = key
            }
            h(
                    this.tagName,
                    data,
                    this.children
                            .map {
                                @Suppress("UnsafeCastFromDynamic")
                                it.toVirtualNode(receiver)
                            }
                            .toTypedArray()
            )
        }
        is Html.Tagger<S, *> -> tagVirtualNode(this, receiver)
    }
}

typealias Update<M, S> = (S, M) -> Pair<M, Cmd<S>?>
typealias View<M, S> = (M) -> Html<S>

class Program<out M, S>(private val root: Element, init: M,
                        private val update: Update<M, S>, private val view: View<M, S>, initialCmd: Cmd<S>?, onAfterRender: ((Element) -> Unit)?) {

    private var requestAnimationFrameId: Int? = null
    private var model = init
    private var previousModel = init
    private var running = true
    private var uiProcessor = arrayOf<() -> Unit>()

    private val _updateView: (Double) -> Unit = { updateView() }
    private val receiver: (S) -> Unit = { msg ->
        if (running) {
            val (newModel, cmd) = update(msg, model)
            if (model !== newModel) {
                model = newModel
                if (requestAnimationFrameId == null) {
                    requestAnimationFrameId = window.requestAnimationFrame(_updateView)
                }
            }
            if (cmd != null) {
                handleCmd(cmd)
            }
        }
    }
    private var virtualNode = h("div", view(model).toVirtualNode(receiver))
    private val patch = if (onAfterRender != null) Snabbdom.init({ onAfterRender(root) }) else Snabbdom.init(null)

    init {
        patch(root, virtualNode)
        if (initialCmd != null) {
            handleCmd(initialCmd)
        }
    }

    private fun updateView() {
        if (!running) {
            return
        }

        requestAnimationFrameId = null
        if (previousModel !== model) {
            previousModel = model
            virtualNode = patch(virtualNode, h("div", view(model).toVirtualNode(receiver)))
        }
        uiProcessor.forEach { it() }
        uiProcessor = arrayOf()
    }

    private fun handleCmd(cmd: Cmd<S>) {
        when (cmd) {
            is Cmd.Promised -> {
                cmd.body().then {
                    receiver(it)
                }
            }
            is Cmd.UiProcessor -> {
                /*
                    UiProcessor 의 동작 타이밍 구분
                    1. View 가 수정 되었거나, Draw 를 기다리고 있을 경우
                        화면에 UI 가 표시된후 Processor 를 호출 한다.
                    2. Cmd 만 왔을 경우
                        현재 표시된 UI 를 기준으로 동작한다.
                 */
                if (requestAnimationFrameId != null) {
                    uiProcessor += cmd.body
                } else {
                    cmd.body()
                }
            }
            is Cmd.CmdList -> {
                cmd.list.forEach(this::handleCmd)
            }
        }
    }

    @JsName("stop")
    fun stop(): M {
        if (running) {
            running = false

            patch(virtualNode, h("div"))
        }
        return model
    }
}

internal fun <M, S> runProgram(root: Element, init: M, update: Update<M, S>, view: View<M, S>, cmd: Cmd<S>? = null, onAfterRender: (Element) -> Unit): Program<M, S> {
    return Program(root, init, update, view, cmd, onAfterRender)
}

@Suppress("unused")
fun <M, S> runProgram(root: Element, init: M, update: Update<M, S>, view: View<M, S>, cmd: Cmd<S>? = null): Program<M, S> {
    return Program(root, init, update, view, cmd, null)
}

@Suppress("unused")
fun <M, S : Any> runProgramDebugger(root: Element, init: M, update: Update<M, S>, view: View<M, S>, cmd: Cmd<S>? = null): Program<DebuggerModel<M, S>, DebuggerMsg<S>> {
    val debugger = debugger(userInit = init, userUpdate = update, userView = view, userCmd = cmd)
    return Program(
            root,
            debugger.init,
            debugger.update,
            debugger.view,
            debugger.cmd,
            null
    )
}

internal fun <M, S> runBeginnerProgram(root: Element, init: M, update: (S, M) -> M, view: (M) -> Html<S>, onAfterRender: (Element) -> Unit): Program<M, S> {
    return Program(root, init, { s, m -> update(s, m) to null }, view, null, onAfterRender)
}

@Suppress("unused")
fun <M, S> runBeginnerProgram(root: Element, init: M, update: (S, M) -> M, view: (M) -> Html<S>): Program<M, S> {
    return Program(root, init, { s, m -> update(s, m) to null }, view, null, null)
}

internal fun runBeginnerProgram(root: Element, view: Html<Unit>, onAfterRender: (Element) -> Unit): Program<Unit, Unit> {
    return Program(root, Unit, { _, _ -> Unit to null }, { view }, null, onAfterRender)
}

@Suppress("unused")
fun runBeginnerProgram(root: Element, view: Html<Unit>): Program<Unit, Unit> {
    return Program(root, Unit, { _, _ -> Unit to null }, { view }, null, null)
}