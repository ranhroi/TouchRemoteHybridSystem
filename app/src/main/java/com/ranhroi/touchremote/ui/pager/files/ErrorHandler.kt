package com.ranhroi.touchremote.ui.pager.files

import android.content.Context
import com.ranhroi.touchremote.R

object ErrorHandler {

    fun handleServerError(response: String, context: Context): Exception {
        return when {
            // ==================== DOWNLOAD ERRORS ====================
            response == "download_error file_not_found" ->
                Exception(context.getString(R.string.download_error_file_not_found))

            response == "download_error permission_denied" ->
                Exception(context.getString(R.string.download_error_permission_denied))

            response == "download_error file_too_large" ->
                Exception(context.getString(R.string.download_error_file_too_large))

            response == "download_error cannot_open" ->
                Exception(context.getString(R.string.download_error_cannot_open))

            response == "download_error folder_not_found" ->
                Exception(context.getString(R.string.download_error_folder_not_found))

            response == "download_error cannot_open_zip" ->
                Exception(context.getString(R.string.download_error_cannot_open_zip))

            response == "download_error compress_failed" ->
                Exception(context.getString(R.string.download_error_compress_failed))

            response == "download_error send_timeout" ->
                Exception(context.getString(R.string.download_error_send_timeout))

            response == "download_error user_cancelled" ->
                Exception(context.getString(R.string.download_error_user_cancelled))

            response == "download_error folder_too_large_for_bluetooth" ->
                Exception(context.getString(R.string.download_error_folder_too_large_for_bluetooth))

            response == "download_error zip_too_large_for_bluetooth" ->
                Exception(context.getString(R.string.download_error_zip_too_large_for_bluetooth))

            // ==================== UPLOAD ERRORS ====================
            response == "upload_error invalid_format" ->
                Exception(context.getString(R.string.upload_error_invalid_format))

            response == "upload_error_invalid_size" ->
                Exception(context.getString(R.string.upload_error_invalid_size))

            response == "upload_error cannot_create_file" ->
                Exception(context.getString(R.string.upload_error_cannot_create_file))

            response == "upload_error cannot_create_dir" ->
                Exception(context.getString(R.string.upload_error_cannot_create_dir))

            response == "upload_error cannot_create_temp_zip" ->
                Exception(context.getString(R.string.upload_error_cannot_create_temp_zip))

            response == "upload_error file_too_large_for_bluetooth" ->
                Exception(context.getString(R.string.upload_error_file_too_large_for_bluetooth))

            response == "upload_error folder_too_large_for_bluetooth" ->
                Exception(context.getString(R.string.upload_error_folder_too_large_for_bluetooth))

            response == "upload_error unzip_failed" ->
                Exception(context.getString(R.string.upload_error_unzip_failed))

            response == "upload_cancelled" ->
                Exception(context.getString(R.string.upload_cancelled))

            response == "upload_failed_incomplete" ->
                Exception(context.getString(R.string.upload_failed_incomplete))

            // ==================== FILE OPERATION ERRORS ====================
            response == "open_failed" ->
                Exception(context.getString(R.string.fileop_open_failed))

            response == "copy_failed" ->
                Exception(context.getString(R.string.fileop_copy_failed))

            response == "cut_failed" ->
                Exception(context.getString(R.string.fileop_cut_failed))

            response == "paste_failed" ->
                Exception(context.getString(R.string.fileop_paste_failed))

            response == "paste_failed_no_source" ->
                Exception(context.getString(R.string.fileop_paste_failed_no_source))

            response == "delete_failed" ->
                Exception(context.getString(R.string.fileop_delete_failed))

            response == "rename_failed" ->
                Exception(context.getString(R.string.fileop_rename_failed))

            response == "mkfile_failed" ->
                Exception(context.getString(R.string.fileop_create_file_failed))

            response == "mkfile_exists" ->
                Exception(context.getString(R.string.fileop_create_file_exists, ""))

            response == "mkdir_failed" ->
                Exception(context.getString(R.string.fileop_create_folder_failed))

            response == "path_not_found" ->
                Exception(context.getString(R.string.fileop_path_not_found))

            response == "details_failed" ->
                Exception(context.getString(R.string.fileop_details_failed))

            // ==================== FOLDER INFO ERROR ====================
            response.startsWith("folder_error") -> Exception(
                context.getString(
                    R.string.folder_info_error,
                    response.substringAfter(" ")
                )
            )

            // ==================== GENERAL ====================
            response.startsWith("download_error") ->
                Exception(
                    context.getString(
                        R.string.download_server_refused,
                        response.substringAfter(" ")
                    )
                )

            response.startsWith("upload_error") ->
                Exception(
                    context.getString(
                        R.string.upload_server_refused,
                        response.substringAfter(" ")
                    )
                )

            else -> Exception(context.getString(R.string.unknown_error, response))
        }
    }

    fun isErrorResponse(response: String): Boolean {
        return response.endsWith("_failed") ||
                response.startsWith("download_error") ||
                response.startsWith("upload_error") ||
                response == "upload_cancelled" ||
                response == "upload_failed_incomplete" ||
                response == "path_not_found" ||
                response.startsWith("folder_error")
    }
}