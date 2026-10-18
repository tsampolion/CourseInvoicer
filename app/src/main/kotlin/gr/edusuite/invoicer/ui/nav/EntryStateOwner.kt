package gr.edusuite.invoicer.ui.nav

import android.os.Bundle
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** Each back-stack entry owns its own registry as well as its ViewModel store. */
internal class EntryStateOwner(
    override val viewModelStore: ViewModelStore,
    restored: Bundle? = null,
) : ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = ViewModelProvider.NewInstanceFactory()
    override val defaultViewModelCreationExtras: CreationExtras get() = MutableCreationExtras().apply {
        set(SAVED_STATE_REGISTRY_OWNER_KEY, this@EntryStateOwner)
        set(VIEW_MODEL_STORE_OWNER_KEY, this@EntryStateOwner)
    }

    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(restored)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun save(): Bundle = Bundle().also(controller::performSave)
}
