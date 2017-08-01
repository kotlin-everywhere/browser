package com.github.kotlin.everywhere.browser

import org.junit.Test
import kotlin.test.assertEquals


class TestView {
    private data class Model(val clicked: Boolean = false)
    private sealed class Msg

    private val init = Model()

    private val update: (Msg, Model) -> Pair<Model, Cmd<Msg>> = { _, model ->
        model to Cmd.none<Msg>()
    }

    private fun serialViewTests(view: (Model) -> Html<Msg>, vararg tests: (root: () -> dynamic) -> Unit) {
        asyncSerialTest(init, update, view, *tests)
    }

    @Test
    fun testBuilderDiv() {
        val view: (Model) -> Html<Msg> = { _ ->
            Html.div {
                div { +"division" }
            }
        }

        serialViewTests(view,
                {
                    assertEquals("<div><div>division</div></div>", it().html())
                }
        )
    }

    @Test
    fun testBuilderButton() {
        val view: (Model) -> Html<Msg> = { _ ->
            Html.div {
                button { +"label" }
            }
        }

        serialViewTests(view,
                {
                    assertEquals("<div><button>label</button></div>", it().html())
                }
        )
    }

    @Test
    fun testTextarea() {
        val view: (Model) -> Html<Msg> = { _ ->
            Html.textarea(text = "<script>alert('danger')</script>")
        }

        serialViewTests(view,
                {
                    assertEquals("<textarea>&lt;script&gt;alert('danger')&lt;/script&gt;</textarea>", it().html())
                }
        )
    }

    @Test
    fun testBuilderTextarea() {
        val view: (Model) -> Html<Msg> = { _ ->
            Html.div {
                textarea(text = "<script>alert('danger')</script>")
            }
        }

        serialViewTests(view,
                {
                    assertEquals("<div><textarea>&lt;script&gt;alert('danger')&lt;/script&gt;</textarea></div>", it().html())
                }
        )
    }

    @Test
    fun testPre() {
        val view: (Model) -> Html<Msg> = { _ ->
            Html.pre(text = "<script>alert('danger')</script>")
        }

        serialViewTests(view,
                {
                    assertEquals("<pre>&lt;script&gt;alert('danger')&lt;/script&gt;</pre>", it().html())
                }
        )
    }

    @Test
    fun testBuilderPre() {
        val view: (Model) -> Html<Msg> = { _ ->
            Html.div {
                pre(text = "<script>alert('danger')</script>")
            }
        }

        serialViewTests(view,
                {
                    assertEquals("<div><pre>&lt;script&gt;alert('danger')&lt;/script&gt;</pre></div>", it().html())
                }
        )
    }


}