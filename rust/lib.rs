use std::ffi::{CStr, CString};
use std::os::raw::c_char;

#[no_mangle]
pub extern "C" fn markdown_to_html(input: *const c_char) -> *mut c_char {
    let input = unsafe { CStr::from_ptr(input) }.to_str().unwrap();
    let result = flatmarkdown::markdown_to_html(input);
    CString::new(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn markdown_to_ast(input: *const c_char) -> *mut c_char {
    let input = unsafe { CStr::from_ptr(input) }.to_str().unwrap();
    let result = flatmarkdown::markdown_to_ast(input);
    CString::new(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "C" fn free_string(ptr: *mut c_char) {
    if !ptr.is_null() {
        unsafe {
            drop(CString::from_raw(ptr));
        }
    }
}
