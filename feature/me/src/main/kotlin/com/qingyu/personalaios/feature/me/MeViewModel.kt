package com.qingyu.personalaios.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingyu.personalaios.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@HiltViewModel class MeViewModel @Inject constructor(private val repository: PersonalSpaceRepository) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val working = MutableStateFlow(false)
    val initialized=MutableStateFlow(false)
    val space = repository.space.onEach { initialized.value=true }.catch { error.value = "空间读取失败，请重新进入重试";initialized.value=true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonalSpace())
    fun avatar(uri: String) { if (!working.value) {
        working.value = true; error.value = null
        viewModelScope.launch {
        try { repository.avatar(uri) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "头像导入失败，请重新选择照片" }
        finally { working.value = false }
    } } }
    fun publish(body: String, images: List<String>, onSaved: () -> Unit) {
        if (working.value || (body.isBlank() && images.isEmpty())) return
        working.value = true; error.value = null
        viewModelScope.launch {
            try { repository.publish(body, images); onSaved() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error.value = "发布失败，内容仍在这里，可以重试" }
            finally { working.value = false }
        }
    }
    fun deletePost(id: String) {
        if(working.value) return
        working.value=true;error.value=null
        viewModelScope.launch {
            try { repository.deletePost(id) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { error.value="动态删除失败，请重试" }
            finally { working.value=false }
        }
    }
    fun rename(value:String,onSaved:()->Unit={}) {
        val name=value.trim()
        if(working.value) return
        if(name.isBlank() || name.length>32) { error.value="名字需要 1–32 个字符";return }
        working.value=true;error.value=null
        viewModelScope.launch {
            try { repository.rename(name);onSaved() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { error.value="名字未能保存，请重试" }
            finally { working.value=false }
        }
    }
}
