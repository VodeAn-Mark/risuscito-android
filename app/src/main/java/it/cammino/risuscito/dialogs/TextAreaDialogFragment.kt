package it.cammino.risuscito.dialogs


import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onShow
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import it.cammino.risuscito.R
import kotlinx.android.synthetic.main.text_area.view.*
import java.io.Serializable

@Suppress("unused")
class TextAreaDialogFragment : DialogFragment() {

    private var mCallback: SimpleInputCallback? = null

    private val builder: Builder?
        get() = if (arguments?.containsKey(BUILDER_TAG) != true) null else arguments?.getSerializable(BUILDER_TAG) as? Builder

    override fun onDestroyView() {
        if (retainInstance)
            dialog?.setDismissMessage(null)
        super.onDestroyView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mBuilder = builder
                ?: throw IllegalStateException("SimpleDialogFragment should be created using its Builder interface.")

        if (mCallback == null)
            mCallback = mBuilder.mListener

        val dialog = MaterialDialog(requireContext())

        if (mBuilder.mTitle != 0)
            dialog.title(res = mBuilder.mTitle)

        if (!mBuilder.mAutoDismiss)
            dialog.noAutoDismiss()

        mBuilder.mPositiveButton?.let {
            dialog.positiveButton(text = it) { mDialog ->
                mCallback?.onPositive(mBuilder.mTag, mDialog)
            }
        }

        mBuilder.mNegativeButton?.let {
            dialog.negativeButton(text = it) { mDialog ->
                mCallback?.onNegative(mBuilder.mTag, mDialog)
            }
        }

        dialog.customView(R.layout.text_area)

        dialog.onShow {
            it.getCustomView().text_area_field.setText(mBuilder.mPrefill)
            it.getCustomView().text_area_field.selectAll()
        }

        dialog.cancelable(mBuilder.mCanceable)

        dialog.setOnKeyListener { arg0, keyCode, event ->
            var returnValue = false
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                arg0.cancel()
                returnValue = true
            }
            returnValue
        }

        return dialog
    }

    fun setmCallback(callback: SimpleInputCallback) {
        mCallback = callback
    }

    fun cancel() {
        dialog?.cancel()
    }

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener) {
        dialog?.setOnCancelListener(listener)
    }

    class Builder(context: AppCompatActivity, @field:Transient var mListener: SimpleInputCallback, val mTag: String) : Serializable {

        @Transient
        private val mContext: AppCompatActivity = context
        @StringRes
        var mTitle = 0
        var mPositiveButton: CharSequence? = null
        var mNegativeButton: CharSequence? = null
        var mCanceable = false
        var mAutoDismiss = true
        var mPrefill: CharSequence? = null

        fun title(@StringRes text: Int): Builder {
            mTitle = text
            return this
        }

        fun prefill(@StringRes text: Int): Builder {
            mPrefill = this.mContext.resources.getText(text)
            return this
        }

        fun prefill(text: String): Builder {
            mPrefill = text
            return this
        }

        fun positiveButton(@StringRes text: Int): Builder {
            mPositiveButton = this.mContext.resources.getText(text)
            return this
        }

        fun negativeButton(@StringRes text: Int): Builder {
            mNegativeButton = this.mContext.resources.getText(text)
            return this
        }

        fun setCanceable(canceable: Boolean): Builder {
            mCanceable = canceable
            return this
        }

        fun setAutoDismiss(autoDismiss: Boolean): Builder {
            mAutoDismiss = autoDismiss
            return this
        }

        fun build(): TextAreaDialogFragment {
            val dialog = TextAreaDialogFragment()
            val args = Bundle()
            args.putSerializable(BUILDER_TAG, this)
            dialog.arguments = args
            return dialog
        }

        fun show(): TextAreaDialogFragment {
            val dialog = build()
            if (!mContext.isFinishing)
                dialog.show(mContext)
            return dialog
        }
    }

    private fun dismissIfNecessary(context: AppCompatActivity, tag: String) {
        val frag = context.supportFragmentManager.findFragmentByTag(tag)
        frag?.let {
            (it as? DialogFragment)?.dismiss()
            context.supportFragmentManager.beginTransaction()
                    .remove(it).commit()
        }
    }

    fun show(context: AppCompatActivity): TextAreaDialogFragment {
        builder?.let {
            dismissIfNecessary(context, it.mTag)
            show(context.supportFragmentManager, it.mTag)
        }
        return this
    }

    interface SimpleInputCallback {
        fun onPositive(tag: String, dialog: MaterialDialog)
        fun onNegative(tag: String, dialog: MaterialDialog)
    }

    companion object {
        private const val BUILDER_TAG = "builder"
        fun findVisible(context: AppCompatActivity?, tag: String): TextAreaDialogFragment? {
            context?.let {
                val frag = it.supportFragmentManager.findFragmentByTag(tag)
                return if (frag != null && frag is TextAreaDialogFragment) frag else null
            }
            return null
        }

        private val TAG = TextAreaDialogFragment::class.java.canonicalName
    }

}