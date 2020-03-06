# \ApiApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**ApiV1SafesCertsGet**](ApiApi.md#ApiV1SafesCertsGet) | **Get** /api/v1/safes/certs | Return each certificate pair that matches a lbl (title) entry, max 100
[**ApiV1SafesDbsGet**](ApiApi.md#ApiV1SafesDbsGet) | **Get** /api/v1/safes/dbs | Return each db that matches a lbl entry, max 100
[**ApiV1SafesEnvsGet**](ApiApi.md#ApiV1SafesEnvsGet) | **Get** /api/v1/safes/envs | Return each environment that matches a lbl entry, max 100
[**ApiV1SafesLoginsGet**](ApiApi.md#ApiV1SafesLoginsGet) | **Get** /api/v1/safes/logins | Return each login that matches a login entry, max 100, logins will search for the login entry, use lbls to search by lbl
[**ApiV1SafesSecretGet**](ApiApi.md#ApiV1SafesSecretGet) | **Get** /api/v1/safes/secret | Return each secret specified by a lbl entry, max 100
[**ApiV1SafesSnippetsGet**](ApiApi.md#ApiV1SafesSnippetsGet) | **Get** /api/v1/safes/snippets | Return each snippet that matches a lbl (title) entry, max 100


# **ApiV1SafesCertsGet**
> []Certificate ApiV1SafesCertsGet(ctx, authorization, safe, lbls)
Return each certificate pair that matches a lbl (title) entry, max 100

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **authorization** | **string**| AppKey:AppSecret or Basic: base64(user:pass) | 
  **safe** | **string**|  | 
  **lbls** | [**[]string**](string.md)|  | 

### Return type

[**[]Certificate**](Certificate.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ApiV1SafesDbsGet**
> []Db ApiV1SafesDbsGet(ctx, authorization, safe, lbls)
Return each db that matches a lbl entry, max 100

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **authorization** | **string**| AppKey:AppSecret or Basic: base64(user:pass) | 
  **safe** | **string**|  | 
  **lbls** | [**[]string**](string.md)|  | 

### Return type

[**[]Db**](DB.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ApiV1SafesEnvsGet**
> []Env ApiV1SafesEnvsGet(ctx, authorization, safe, lbls)
Return each environment that matches a lbl entry, max 100

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **authorization** | **string**| AppKey:AppSecret or Basic: base64(user:pass) | 
  **safe** | **string**|  | 
  **lbls** | [**[]string**](string.md)|  | 

### Return type

[**[]Env**](Env.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ApiV1SafesLoginsGet**
> []Login ApiV1SafesLoginsGet(ctx, authorization, safe, logins, lbls)
Return each login that matches a login entry, max 100, logins will search for the login entry, use lbls to search by lbl

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **authorization** | **string**| AppKey:AppSecret or Basic: base64(user:pass) | 
  **safe** | **string**|  | 
  **logins** | [**[]string**](string.md)|  | 
  **lbls** | [**[]string**](string.md)|  | 

### Return type

[**[]Login**](Login.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ApiV1SafesSecretGet**
> []Secret ApiV1SafesSecretGet(ctx, authorization, safe, lbls)
Return each secret specified by a lbl entry, max 100

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **authorization** | **string**| AppKey:AppSecret or Basic: base64(user:pass) | 
  **safe** | **string**|  | 
  **lbls** | [**[]string**](string.md)|  | 

### Return type

[**[]Secret**](Secret.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ApiV1SafesSnippetsGet**
> []Snippet ApiV1SafesSnippetsGet(ctx, authorization, safe, lbls)
Return each snippet that matches a lbl (title) entry, max 100

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **authorization** | **string**| AppKey:AppSecret or Basic: base64(user:pass) | 
  **safe** | **string**|  | 
  **lbls** | [**[]string**](string.md)|  | 

### Return type

[**[]Snippet**](Snippet.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

