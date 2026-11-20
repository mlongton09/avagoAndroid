package com.avago.feature.settings

import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.Category
import com.avago.core.data.domain.CategoryDraft
import com.avago.core.data.domain.CategoryKind
import com.avago.core.data.domain.CustomFieldDef
import com.avago.core.data.domain.CustomFieldDefDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Custom field definitions for assets, products and work orders. */
@HiltViewModel
class CustomFieldDefsViewModel @Inject constructor(
    private val admin: AdminRepository,
) : AdminViewModel() {

    val fields: StateFlow<List<CustomFieldDef>?> = admin.observeCustomFieldDefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canEdit: StateFlow<Boolean> = admin.observeAllowed(EDIT_SETTINGS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Creates a field when [id] is null, otherwise updates it (type and entity are fixed). */
    fun save(id: String?, draft: CustomFieldDefDraft) = submit {
        if (id == null) admin.createCustomFieldDef(draft) else admin.updateCustomFieldDef(id, draft)
    }

    fun delete(id: String) = submit { admin.deleteCustomFieldDef(id) }
}

/** Work-order, product, asset and request categories. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val admin: AdminRepository,
) : AdminViewModel() {

    private val _kind = MutableStateFlow(CategoryKind.WORK_ORDER)
    val kind: StateFlow<CategoryKind> = _kind.asStateFlow()

    val categories: StateFlow<List<Category>?> = _kind.flatMapLatest { admin.observeCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Settings editors may change every kind; template managers the work-order categories. */
    val canEditSettings: StateFlow<Boolean> = admin.observeAllowed(EDIT_SETTINGS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canManageTemplates: StateFlow<Boolean> = admin.observeAllowed(MANAGE_TEMPLATES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun selectKind(kind: CategoryKind) {
        _kind.value = kind
    }

    fun save(id: String?, draft: CategoryDraft) = submit {
        if (id == null) admin.createCategory(_kind.value, draft) else admin.updateCategory(id, draft)
    }

    fun delete(id: String) = submit { admin.deleteCategory(id) }

    private companion object {
        const val MANAGE_TEMPLATES = "work_orders.manage_templates"
    }
}

private const val EDIT_SETTINGS = "account.edit_settings"
