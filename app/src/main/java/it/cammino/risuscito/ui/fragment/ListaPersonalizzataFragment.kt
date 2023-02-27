package it.cammino.risuscito.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityOptionsCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import it.cammino.risuscito.R
import it.cammino.risuscito.database.RisuscitoDatabase
import it.cammino.risuscito.database.entities.ListaPers
import it.cammino.risuscito.databinding.ActivityListaPersonalizzataBinding
import it.cammino.risuscito.items.ListaPersonalizzataItem
import it.cammino.risuscito.ui.activity.InsertActivity
import it.cammino.risuscito.ui.activity.MainActivity
import it.cammino.risuscito.ui.dialog.BottomSheetFragment
import it.cammino.risuscito.ui.dialog.DialogState
import it.cammino.risuscito.ui.dialog.InputTextDialogFragment
import it.cammino.risuscito.utils.OSUtils
import it.cammino.risuscito.utils.Utility
import it.cammino.risuscito.utils.extension.listToXML
import it.cammino.risuscito.utils.extension.openCanto
import it.cammino.risuscito.utils.extension.slideInRight
import it.cammino.risuscito.utils.extension.systemLocale
import it.cammino.risuscito.viewmodels.ListaPersonalizzataViewModel
import it.cammino.risuscito.viewmodels.ViewModelWithArgumentsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListaPersonalizzataFragment : Fragment() {

    private val mCantiViewModel: ListaPersonalizzataViewModel by viewModels {
        ViewModelWithArgumentsFactory(requireActivity().application, Bundle().apply {
            putInt(Utility.TIPO_LISTA, arguments?.getInt(INDICE_LISTA) ?: 0)
        })
    }
    private val inputdialogViewModel: InputTextDialogFragment.DialogViewModel by viewModels({ requireActivity() })

    private lateinit var cantoDaCanc: String
    private lateinit var notaDaCanc: String
    private var mSwhitchMode: Boolean = false
    private var longclickedPos: Int = 0
    private var longClickedChild: Int = 0
    private val cantoAdapter: FastItemAdapter<ListaPersonalizzataItem> = FastItemAdapter()
    private var actionModeOk: Boolean = false
    private var mMainActivity: MainActivity? = null
    private var mLastClickTime: Long = 0

    private var _binding: ActivityListaPersonalizzataBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityListaPersonalizzataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mMainActivity = activity as? MainActivity

        mSwhitchMode = false

        // Creating new adapter object
        cantoAdapter.setHasStableIds(true)
        cantoAdapter.set(mCantiViewModel.posizioniList)
        binding.recyclerList.adapter = cantoAdapter

        // Setting the layoutManager
        binding.recyclerList.layoutManager = LinearLayoutManager(activity)

        subscribeUiChanges()

        binding.buttonPulisci.setOnClickListener {
            for (i in 0 until (mCantiViewModel.listaPersonalizzata?.numPosizioni ?: 0))
                mCantiViewModel.listaPersonalizzata?.removeCanto(i)
            runUpdate()
        }

        binding.buttonCondividi.setOnClickListener {
            val bottomSheetDialog = BottomSheetFragment.newInstance(R.string.share_by, shareIntent)
            bottomSheetDialog.show(parentFragmentManager, null)
        }

        binding.buttonInviaFile.setOnClickListener {
            val exportUri = activity?.listToXML(mCantiViewModel.listaPersonalizzata)
            Log.d(TAG, "onClick: exportUri = $exportUri")
            exportUri?.let {
                val bottomSheetDialog =
                    BottomSheetFragment.newInstance(R.string.share_by, getSendIntent(it))
                bottomSheetDialog.show(parentFragmentManager, null)
            } ?: run {
                mMainActivity?.let {
                    Snackbar.make(it.activityMainContent, R.string.xml_error, Snackbar.LENGTH_LONG)
                        .show()
                }
            }
        }

    }

    private fun getSendIntent(exportUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, exportUri)
            .setType("text/xml")
    }

    private fun openPagina(v: View) {
        // crea un bundle e ci mette il parametro "pagina", contente il nome del file della pagina da
        // visualizzare
        mMainActivity?.openCanto(
            TAG,
            v,
            Integer.valueOf(v.findViewById<TextView>(R.id.text_id_canto_card).text.toString()),
            v.findViewById<TextView>(R.id.text_source_canto).text.toString(),
            false
        )
    }

    private fun snackBarRimuoviCanto(view: View) {
        mMainActivity?.actionMode?.finish()
        val parent = view.parent.parent as? View
        longclickedPos =
            Integer.valueOf(parent?.findViewById<TextView>(R.id.generic_tag)?.text.toString())
        longClickedChild =
            Integer.valueOf(view.findViewById<TextView>(R.id.item_tag).text.toString())
        startCab()
    }

    private fun scambioCanto(posizioneNew: Int) {
        if (posizioneNew != mCantiViewModel.posizioneDaCanc) {

            val cantoTmp = mCantiViewModel.listaPersonalizzata?.getCantoPosizione(posizioneNew)
            val notaTmp = mCantiViewModel.listaPersonalizzata?.getNotaPosizione(posizioneNew)
            mCantiViewModel.listaPersonalizzata?.addCanto(
                mCantiViewModel.listaPersonalizzata?.getCantoPosizione(mCantiViewModel.posizioneDaCanc),
                posizioneNew
            )
            mCantiViewModel.listaPersonalizzata?.addNota(
                mCantiViewModel.listaPersonalizzata?.getNotaPosizione(mCantiViewModel.posizioneDaCanc),
                posizioneNew
            )
            mCantiViewModel.listaPersonalizzata?.addCanto(cantoTmp, mCantiViewModel.posizioneDaCanc)
            mCantiViewModel.listaPersonalizzata?.addNota(notaTmp, mCantiViewModel.posizioneDaCanc)

            runUpdate()

            actionModeOk = true
            mMainActivity?.actionMode?.finish()
            mMainActivity?.let {
                Snackbar.make(it.activityMainContent, R.string.switch_done, Snackbar.LENGTH_SHORT)
                    .show()
            }

        } else
            mMainActivity?.let {
                Snackbar.make(
                    it.activityMainContent,
                    R.string.switch_impossible,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
    }

    private fun scambioConVuoto(posizioneNew: Int) {
        //        Log.i(getClass().toString(), "positioneNew: " + posizioneNew);
        //        Log.i(getClass().toString(), "posizioneDaCanc: " + posizioneDaCanc);
        mCantiViewModel.listaPersonalizzata?.addCanto(
            mCantiViewModel.listaPersonalizzata?.getCantoPosizione(mCantiViewModel.posizioneDaCanc),
            posizioneNew
        )
        mCantiViewModel.listaPersonalizzata?.addNota(
            mCantiViewModel.listaPersonalizzata?.getNotaPosizione(mCantiViewModel.posizioneDaCanc),
            posizioneNew
        )
        mCantiViewModel.listaPersonalizzata?.removeCanto(mCantiViewModel.posizioneDaCanc)
        mCantiViewModel.listaPersonalizzata?.removeNota(mCantiViewModel.posizioneDaCanc)

        runUpdate()

        actionModeOk = true
        mMainActivity?.actionMode?.finish()
        mMainActivity?.let {
            Snackbar.make(it.activityMainContent, R.string.switch_done, Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun startCab() {
        mSwhitchMode = false

        val callback = object : ActionMode.Callback {

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
                activity?.menuInflater?.inflate(R.menu.menu_actionmode_lists, menu)
                mCantiViewModel.posizioniList[longclickedPos].listItem?.get(longClickedChild)
                    ?.setmSelected(true)
                cantoAdapter.notifyItemChanged(longclickedPos)
                actionModeOk = false
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                Log.d(TAG, "MaterialCab onActionItemClicked")
                return when (item?.itemId) {
                    R.id.action_remove_item -> {
                        cantoDaCanc =
                            mCantiViewModel.listaPersonalizzata?.getCantoPosizione(mCantiViewModel.posizioneDaCanc)
                                .orEmpty()
                        notaDaCanc =
                            mCantiViewModel.listaPersonalizzata?.getNotaPosizione(mCantiViewModel.posizioneDaCanc)
                                .orEmpty()
                        mCantiViewModel.listaPersonalizzata?.removeCanto(mCantiViewModel.posizioneDaCanc)
                        mCantiViewModel.listaPersonalizzata?.removeNota(mCantiViewModel.posizioneDaCanc)
                        runUpdate()
                        actionModeOk = true
                        mMainActivity?.actionMode?.finish()
                        mMainActivity?.let {
                            Snackbar.make(
                                it.activityMainContent,
                                R.string.song_removed,
                                Snackbar.LENGTH_LONG
                            )
                                .setAction(
                                    getString(R.string.cancel).uppercase(resources.systemLocale)
                                ) {
                                    mCantiViewModel.listaPersonalizzata?.addCanto(
                                        cantoDaCanc,
                                        mCantiViewModel.posizioneDaCanc
                                    )
                                    mCantiViewModel.listaPersonalizzata?.addNota(
                                        notaDaCanc,
                                        mCantiViewModel.posizioneDaCanc
                                    )
                                    runUpdate()
                                }
                                .show()
                        }
                        true
                    }
                    R.id.action_switch_item -> {
                        cantoDaCanc =
                            mCantiViewModel.listaPersonalizzata?.getCantoPosizione(mCantiViewModel.posizioneDaCanc)
                                .orEmpty()
                        notaDaCanc =
                            mCantiViewModel.listaPersonalizzata?.getNotaPosizione(mCantiViewModel.posizioneDaCanc)
                                .orEmpty()
                        mSwhitchMode = true
                        updateActionModeTitle(true)
                        Toast.makeText(
                            activity,
                            resources.getString(R.string.switch_tooltip),
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                Log.d(TAG, "MaterialCab onDestroy: $actionModeOk")
                Log.d(
                    TAG,
                    "MaterialCab onDestroy - longclickedPos: $longclickedPos / listaPersonalizzataId: ${mCantiViewModel.listaPersonalizzataId}"
                )
                mSwhitchMode = false
                if (!actionModeOk) {
                    try {
                        mCantiViewModel.posizioniList[longclickedPos].listItem?.get(longClickedChild)
                            ?.setmSelected(false)
                        cantoAdapter.notifyItemChanged(longclickedPos)
                    } catch (e: Exception) {
                        Firebase.crashlytics.recordException(e)
                    }
                }
                mMainActivity?.destroyActionMode()
            }

        }

        mMainActivity?.createActionMode(callback)
        updateActionModeTitle(false)
    }

    private fun updateActionModeTitle(switchMode: Boolean) {
        mMainActivity?.updateActionModeTitle(
            if (switchMode)
                resources.getString(R.string.switch_started)
            else
                resources.getQuantityString(R.plurals.item_selected, 1, 1)
        )
    }

    private fun runUpdate() {
        mMainActivity?.let {
            val listaNew = ListaPers()
            listaNew.lista = mCantiViewModel.listaPersonalizzata
            listaNew.id = mCantiViewModel.listaPersonalizzataId
            listaNew.titolo = mCantiViewModel.listaPersonalizzataTitle
            val mDao = RisuscitoDatabase.getInstance(it).listePersDao()
            lifecycleScope.launch(Dispatchers.IO) { mDao.updateLista(listaNew) }
        }
    }

    private fun subscribeUiChanges() {
        mCantiViewModel.listaPersonalizzataResult?.observe(viewLifecycleOwner) { listaPersonalizzataResult ->
            mCantiViewModel.posizioniList = listaPersonalizzataResult.map {
                it.apply {
                    createClickListener = click
                    createLongClickListener = longClick
                    editNoteClickListener = noteClick
                }
            }
            cantoAdapter.set(mCantiViewModel.posizioniList)
        }

        inputdialogViewModel.state.observe(viewLifecycleOwner) {
            Log.d(TAG, "inputdialogViewModel state $it")
            if (!inputdialogViewModel.handled) {
                when (it) {
                    is DialogState.Positive -> {
                        when (inputdialogViewModel.mTag) {
                            EDIT_NOTE + mCantiViewModel.listaPersonalizzataId -> {
                                inputdialogViewModel.handled = true
                                Log.d(
                                    TAG,
                                    "inputdialogViewModel.outputText ${inputdialogViewModel.outputText}"
                                )
                                Log.d(
                                    TAG,
                                    " mCantiViewModel.posizioneDaCanc ${mCantiViewModel.posizioneDaCanc}"
                                )
                                mCantiViewModel.listaPersonalizzata?.addNota(
                                    inputdialogViewModel.outputText,
                                    inputdialogViewModel.outputItemId
                                )
                                runUpdate()
                                mMainActivity?.activityMainContent?.let { v ->
                                    Snackbar.make(
                                        v,
                                        R.string.edit_note_confirm_message,
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    is DialogState.Negative -> {
                        inputdialogViewModel.handled = true
                    }
                }
            }
        }
    }

    private val shareIntent: Intent
        get() = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, titlesList)
            .setType("text/plain")

    private val titlesList: String
        get() {
            val l = resources.systemLocale
            val result = StringBuilder()
            result.append("-- ").append(mCantiViewModel.listaPersonalizzata?.name?.uppercase(l))
                .append(" --\n")
            for (i in 0 until (mCantiViewModel.listaPersonalizzata?.numPosizioni ?: 0)) {
                result.append(
                    mCantiViewModel.listaPersonalizzata?.getNomePosizione(i)?.uppercase(l)
                ).append("\n")
                if (!mCantiViewModel.listaPersonalizzata?.getCantoPosizione(i).isNullOrEmpty()) {
                    mCantiViewModel.posizioniList[i].listItem?.let {
                        for (tempItem in it) {
                            result
                                .append(tempItem.title?.getText(requireContext()))
                                .append(" - ")
                                .append(getString(R.string.page_contracted))
                                .append(tempItem.page?.getText(requireContext()))
                            if (tempItem.nota.isNotEmpty())
                                result.append(" (")
                                    .append(tempItem.nota)
                                    .append(")")
                            result.append("\n")
                        }
                    }
                } else {
                    result.append(">> ").append(getString(R.string.to_be_chosen)).append(" <<")
                    result.append("\n")
                }
                if (i < (mCantiViewModel.listaPersonalizzata?.numPosizioni
                        ?: 0) - 1
                ) result.append("\n")
            }

            return result.toString()
        }

    private val startListInsertForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == CustomListsFragment.RESULT_OK || result.resultCode == CustomListsFragment.RESULT_KO)
                mMainActivity?.activityMainContent?.let {
                    Snackbar.make(
                        it,
                        if (result.resultCode == CustomListsFragment.RESULT_OK) R.string.list_added else R.string.present_yet,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
        }

    private val click = OnClickListener { v ->
        if (SystemClock.elapsedRealtime() - mLastClickTime >= Utility.CLICK_DELAY) {
            mLastClickTime = SystemClock.elapsedRealtime()
            val parent = v.parent.parent as? View
            if (parent?.findViewById<View>(R.id.add_canto_generico)?.isVisible == true) {
                if (mSwhitchMode) {
                    scambioConVuoto(
                        Integer.valueOf(parent.findViewById<TextView>(R.id.text_id_posizione).text.toString())
                    )
                } else {
                    if (mMainActivity?.actionMode == null) {
                        val intent = Intent(activity, InsertActivity::class.java)
                        intent.putExtras(
                            bundleOf(
                                InsertActivity.FROM_ADD to 0,
                                InsertActivity.ID_LISTA to mCantiViewModel.listaPersonalizzataId,
                                InsertActivity.POSITION to Integer.valueOf(
                                    parent.findViewById<TextView>(
                                        R.id.text_id_posizione
                                    ).text.toString()
                                )
                            )
                        )
                        mMainActivity?.let {
                            if (OSUtils.isObySamsung()) {
                                startListInsertForResult.launch(intent)
                                it.slideInRight()
                            } else {
                                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    it,
                                    v,
                                    "shared_insert_container" // The transition name to be matched in Activity B.
                                )
                                startListInsertForResult.launch(intent, options)
                            }
                        }
                    }
                }
            } else {
                if (!mSwhitchMode)
                    if (mMainActivity?.actionMode != null) {
                        mCantiViewModel.posizioneDaCanc =
                            Integer.valueOf(parent?.findViewById<TextView>(R.id.text_id_posizione)?.text.toString())
                        snackBarRimuoviCanto(v)
                    } else
                        openPagina(v)
                else {
                    scambioCanto(
                        Integer.valueOf(parent?.findViewById<TextView>(R.id.text_id_posizione)?.text.toString())
                    )
                }
            }
        }
    }

    private val longClick = OnLongClickListener { v ->
        val parent = v.parent.parent as? View
        mCantiViewModel.posizioneDaCanc =
            Integer.valueOf(parent?.findViewById<TextView>(R.id.text_id_posizione)?.text.toString())
        snackBarRimuoviCanto(v)
        true
    }

    private val noteClick = object : ListaPersonalizzataItem.NoteClickListener {
        override fun onclick(idPosizione: Int, nota: String, idCanto: Int) {
            mMainActivity?.let { mActivity ->
                InputTextDialogFragment.show(
                    InputTextDialogFragment.Builder(
                        EDIT_NOTE + mCantiViewModel.listaPersonalizzataId
                    ).apply {
                        title = R.string.edit_note_title
                        positiveButton = R.string.action_salva
                        negativeButton = R.string.cancel
                        prefill = nota
                        itemId = idPosizione
                        multiLine = true
                    }, mActivity.supportFragmentManager
                )
            }
        }
    }

    companion object {
        internal val TAG = ListaPersonalizzataFragment::class.java.canonicalName
        private const val INDICE_LISTA = "indiceLista"
        private const val EDIT_NOTE = "EDIT_NOTE"

        fun newInstance(indiceLista: Int): ListaPersonalizzataFragment {
            val f = ListaPersonalizzataFragment()
            f.arguments = bundleOf(INDICE_LISTA to indiceLista)
            return f
        }
    }
}
