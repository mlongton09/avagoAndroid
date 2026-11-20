package com.avago.feature.settings

import androidx.lifecycle.viewModelScope
import com.avago.core.data.domain.AccountMember
import com.avago.core.data.domain.AdminRepository
import com.avago.core.data.domain.PermissionSet
import com.avago.core.data.domain.PermissionSetDraft
import com.avago.core.data.v2.V2GeneratedPermissions
import com.avago.core.data.v2.V2PermissionKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PermissionSetsViewModel @Inject constructor(
    private val admin: AdminRepository,
) : AdminViewModel() {

    val sets: StateFlow<List<PermissionSet>?> = admin.observePermissionSets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members: StateFlow<List<AccountMember>> = admin.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val canManage: StateFlow<Boolean> = admin.observeAllowed(MANAGE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val canAssign: StateFlow<Boolean> = admin.observeAllowed(ASSIGN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The permission keys a set can hold, by area. */
    val keysByArea: Map<String, List<V2PermissionKey>> = V2GeneratedPermissions.keys.groupBy { it.area }.toSortedMap()

    /** Creates a set when [id] is null, otherwise updates it. */
    fun save(id: String?, draft: PermissionSetDraft) = submit {
        if (id == null) admin.createPermissionSet(draft) else admin.updatePermissionSet(id, draft)
    }

    fun delete(id: String) = submit { admin.deletePermissionSet(id) }

    fun assign(userId: String, permissionSetId: String?) = submit { admin.assignPermissionSet(userId, permissionSetId) }

    private companion object {
        const val MANAGE = "admin.manage_permission_sets"
        const val ASSIGN = "member.assign_permission_set"
    }
}
