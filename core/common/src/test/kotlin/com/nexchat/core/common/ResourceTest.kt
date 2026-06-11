package com.nexchat.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResourceTest {

    @Test
    fun `TestResource_Success_onSuccess_called`() {
        val resource = Resource.Success("TestData")
        
        var successData: String? = null
        if (resource is Resource.Success) {
            successData = resource.data
        }
        
        assertThat(successData).isEqualTo("TestData")
    }

    @Test
    fun `TestResource_Error_onError_called`() {
        val exception = Exception("Network failure")
        val resource = Resource.Error(exception)
        
        var errorMsg: String? = null
        if (resource is Resource.Error) {
            errorMsg = resource.message
        }
        
        assertThat(errorMsg).isEqualTo("Network failure")
    }
}
