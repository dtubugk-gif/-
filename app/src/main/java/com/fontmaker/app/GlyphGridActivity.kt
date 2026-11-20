package com.fontmaker.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

class GlyphGridActivity : Activity() {
    private lateinit var store: FontStore
    private lateinit var font: HandFont
    private lateinit var grid: GridView
    private lateinit var header: TextView

    private fun dp(v: Int) = Ui.dpi(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FontStore(this)
        font = store.active()

        header = Ui.label(this, "", 15f, Ui.TEXT_SOFT)
        grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(72)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(4)
            horizontalSpacing = dp(4)
            adapter = Adapter()
            setOnItemClickListener { _, _, pos, _ ->
                startActivity(Intent(this@GlyphGridActivity, DrawActivity::class.java)
                    .putExtra(DrawActivity.EXTRA_CHAR, CharacterSet.ALL[pos]))
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(12), dp(16), dp(12), 0)
            addView(Ui.label(context, "All characters", 26f, Ui.TEXT, bold = true))
            addView(header)
            addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        font = store.active()
        header.text = "${font.name}: ${font.drawnCount} of ${CharacterSet.ALL.size} drawn. Tap one to draw it."
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = CharacterSet.ALL.size
        override fun getItem(position: Int) = CharacterSet.ALL[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = (convertView as? GlyphCellView) ?: GlyphCellView(this@GlyphGridActivity)
            v.font = font
            v.ch = CharacterSet.ALL[position]
            return v
        }
    }
}
