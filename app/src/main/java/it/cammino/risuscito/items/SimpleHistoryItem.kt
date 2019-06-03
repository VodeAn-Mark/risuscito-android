package it.cammino.risuscito.items

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils
import com.mikepenz.materialize.holder.ColorHolder
import com.mikepenz.materialize.holder.StringHolder
import com.mikepenz.materialize.util.UIUtils
import it.cammino.risuscito.R
import it.cammino.risuscito.ui.ThemeableActivity
import kotlinx.android.synthetic.main.row_item_history.view.*
import java.sql.Date
import java.text.DateFormat
import java.text.SimpleDateFormat

@Suppress("unused")
class SimpleHistoryItem : AbstractItem<SimpleHistoryItem.ViewHolder>() {

    var title: StringHolder? = null
        private set
    var page: StringHolder? = null
        private set
    var timestamp: StringHolder? = null
        private set
    var source: StringHolder? = null
        private set
    var color: ColorHolder? = null
        private set
    private var selectedColor: ColorHolder? = null
    var id: Int = 0
        private set

    fun withTitle(title: String): SimpleHistoryItem {
        this.title = StringHolder(title)
        return this
    }

    fun withTitle(@StringRes titleRes: Int): SimpleHistoryItem {
        this.title = StringHolder(titleRes)
        return this
    }

    fun withPage(page: String): SimpleHistoryItem {
        this.page = StringHolder(page)
        return this
    }

    fun withPage(@StringRes pageRes: Int): SimpleHistoryItem {
        this.page = StringHolder(pageRes)
        return this
    }

    fun withTimestamp(timestamp: String): SimpleHistoryItem {
        this.timestamp = StringHolder(timestamp)
        return this
    }

    fun withSource(src: String): SimpleHistoryItem {
        this.source = StringHolder(src)
        return this
    }

    fun withSource(@StringRes srcRes: Int): SimpleHistoryItem {
        this.source = StringHolder(srcRes)
        return this
    }

    fun withColor(color: String): SimpleHistoryItem {
        this.color = ColorHolder.fromColor(Color.parseColor(color))
        return this
    }

    fun withColor(@ColorRes colorRes: Int): SimpleHistoryItem {
        this.color = ColorHolder.fromColorRes(colorRes)
        return this
    }

    fun withId(id: Int): SimpleHistoryItem {
        this.id = id
        identifier = id.toLong()
        return this
    }

    fun withSelectedColor(selectedColor: String): SimpleHistoryItem {
        this.selectedColor = ColorHolder.fromColor(Color.parseColor(selectedColor))
        return this
    }

    fun withSelectedColor(@ColorInt selectedColor: Int): SimpleHistoryItem {
        this.selectedColor = ColorHolder.fromColor(selectedColor)
        return this
    }

    fun withSelectedColorRes(@ColorRes selectedColorRes: Int): SimpleHistoryItem {
        this.selectedColor = ColorHolder.fromColorRes(selectedColorRes)
        return this
    }

    /**
     * defines the type defining this item. must be unique. preferably an id
     *
     * @return the type
     */
    override val type: Int
        get() = R.id.fastadapter_history_item_id

    /**
     * defines the layout which will be used for this item in the list
     *
     * @return the layout for this item
     */
    override val layoutRes: Int
        get() = R.layout.row_item_history

    /**
     * binds the data of this item onto the viewHolder
     *
     * @param holder the viewHolder of this item
     */
    override fun bindView(holder: ViewHolder, payloads: MutableList<Any>) {
        super.bindView(holder, payloads)

        // get the context
        val ctx = holder.itemView.context

        // set the text for the name
        StringHolder.applyTo(title, holder.mTitle)
        // set the text for the description or hide
        StringHolder.applyToOrHide(page, holder.mPage)

        @Suppress("DEPRECATION")
        UIUtils.setBackground(
                holder.view,
                FastAdapterUIUtils.getSelectableBackground(
                        ctx,
                        ContextCompat.getColor(holder.itemView.context, R.color.ripple_color),
                        true))

        if (isSelected) {
            holder.mPage?.visibility = View.INVISIBLE
            holder.mPageSelected?.visibility = View.VISIBLE
            val bgShape = holder.mPageSelected?.background as? GradientDrawable
            bgShape?.setColor(selectedColor?.colorInt ?: Color.WHITE)
        } else {
            val bgShape = holder.mPage?.background as? GradientDrawable
            bgShape?.setColor(color?.colorInt ?: Color.WHITE)
            holder.mPage?.visibility = View.VISIBLE
            holder.mPageSelected?.visibility = View.INVISIBLE
        }

        holder.mId?.text = id.toString()

        if (timestamp != null) {
            // FORMATTO LA DATA IN BASE ALLA LOCALIZZAZIONE
            val df = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.MEDIUM, ThemeableActivity.getSystemLocalWrapper(ctx.resources.configuration))
            val tempTimestamp: String

            val dateTimestamp = Date(java.lang.Long.parseLong(timestamp?.text.toString()))
            tempTimestamp = if (df is SimpleDateFormat) {
                val pattern = df.toPattern().replace("y+".toRegex(), "yyyy")
                df.applyPattern(pattern)
                df.format(dateTimestamp)
            } else
                df.format(dateTimestamp)
            holder.mTimestamp?.text = tempTimestamp
            holder.mTimestamp?.visibility = View.VISIBLE
        } else
            holder.mTimestamp?.visibility = View.GONE
    }

    override fun unbindView(holder: ViewHolder) {
        super.unbindView(holder)
        holder.mTitle?.text = null
        holder.mPage?.text = null
        holder.mId?.text = null
        holder.mTimestamp?.text = null
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    /** our ViewHolder  */
    class ViewHolder(var view: View) : RecyclerView.ViewHolder(view) {

        var mTitle: TextView? = null
        var mPage: TextView? = null
        var mPageSelected: View? = null
        var mTimestamp: TextView? = null
        var mId: TextView? = null

        init {
            mTitle = view.text_title
            mPage = view.text_page
            mPageSelected = view.selected_mark
            mTimestamp = view.text_timestamp
            mId = view.text_id_canto
        }
    }
}
