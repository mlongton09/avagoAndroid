package com.avago.feature.docs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.Document
import com.avago.core.data.domain.DocumentKind
import com.avago.core.data.domain.DocumentRepository
import com.avago.feature.docs.model.DocumentFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/** Every maintenance document of the account (synced `documents`), filtered by kind and text. */
@HiltViewModel
class DocListViewModel @Inject constructor(documents: DocumentRepository) : ViewModel() {

    private val _filter = MutableStateFlow(DocumentFilter())
    val filter: StateFlow<DocumentFilter> = _filter.asStateFlow()

    private val all = documents.observeDocuments()
        .catch { e -> Timber.e(e, "[DocListViewModel] document flow error"); emit(emptyList()) }

    /** Null until the first read. */
    val documents: StateFlow<List<Document>?> = combine(all, _filter) { list, f -> f.apply(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onKind(kind: DocumentKind?) {
        _filter.value = _filter.value.copy(kind = kind)
    }

    fun onQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }
}
