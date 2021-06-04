package plugin

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldDataInvalidException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.valuepair.ImageFormats
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentFieldKey
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.vorbiscomment.util.Base64Coder
import java.io.File
import java.io.RandomAccessFile
import java.util.*

class Plugin {

    private val context: Context? = null

    private fun writeTags(path: String, map: Map<String?, String?>, artwork: String?): Boolean {
        try {
            val mp3File = File(path)
            val audioFile = AudioFileIO.read(mp3File)
            var newTag = audioFile.tag
            if (newTag == null) throw Exception("File tag not found")

            // Convert ID3v1 tag to ID3v23
            if (audioFile is MP3File) {
                if (audioFile.hasID3v1Tag() && !audioFile.hasID3v2Tag()) {
                    newTag = ID3v23Tag(audioFile.iD3v1Tag)
                    audioFile.iD3v1Tag = null // remove v1 tags
                    audioFile.tag = newTag // add v2 tags
                }
            }
            Util.setFieldIfExist(newTag, FieldKey.TITLE, map, "title")
            Util.setFieldIfExist(newTag, FieldKey.ARTIST, map, "artist")
            Util.setFieldIfExist(newTag, FieldKey.GENRE, map, "genre")
            Util.setFieldIfExist(newTag, FieldKey.TRACK, map, "trackNumber")
            Util.setFieldIfExist(newTag, FieldKey.TRACK_TOTAL, map, "trackTotal")
            Util.setFieldIfExist(newTag, FieldKey.DISC_NO, map, "discNumber")
            Util.setFieldIfExist(newTag, FieldKey.DISC_TOTAL, map, "discTotal")
            Util.setFieldIfExist(newTag, FieldKey.LYRICS, map, "lyrics")
            Util.setFieldIfExist(newTag, FieldKey.COMMENT, map, "comment")
            Util.setFieldIfExist(newTag, FieldKey.ALBUM, map, "album")
            Util.setFieldIfExist(newTag, FieldKey.ALBUM_ARTIST, map, "albumArtist")
            Util.setFieldIfExist(newTag, FieldKey.YEAR, map, "year")
            var cover: Artwork?
            // If field is null, it is ignored
            if (artwork != null) {
                // If field is set to an empty string, the field is deleted, otherwise it is set
                if (artwork.trim().isNotEmpty()) {

                    // Delete existing album art
                    newTag.deleteArtworkField()

                    // The following content is treated specially
                    cover = ArtworkFactory.createArtworkFromFile(File(artwork))
                    if (newTag is Mp4Tag) {
                        val imageFile = RandomAccessFile(File(artwork), "r")
                        val imageData = ByteArray(
                            imageFile.length().toInt()
                        )
                        imageFile.read(imageData)
                        newTag.setField(newTag.createArtworkField(imageData))
                    } else if (newTag is FlacTag) {
                        val imageFile = RandomAccessFile(File(artwork), "r")
                        val imageData = ByteArray(
                            imageFile.length().toInt()
                        )
                        imageFile.read(imageData)
                        newTag.setField(
                            newTag.createArtworkField(
                                imageData,
                                PictureTypes.DEFAULT_ID,
                                ImageFormats.MIME_TYPE_JPEG,
                                "artwork",
                                0,
                                0,
                                24,
                                0
                            )
                        )
                    } else if (newTag is VorbisCommentTag) {
                        val imageFile = RandomAccessFile(File(artwork), "r")
                        val imageData = ByteArray(
                            imageFile.length().toInt()
                        )
                        imageFile.read(imageData)
                        val base64Data = Base64Coder.encode(imageData)
                        val base64image = String(base64Data)
                        newTag.setField(
                            newTag.createField(
                                VorbisCommentFieldKey.COVERART,
                                base64image
                            )
                        )
                        newTag.setField(
                            newTag.createField(
                                VorbisCommentFieldKey.COVERARTMIME,
                                "image/png"
                            )
                        )
                    } else {
                        cover = ArtworkFactory.createArtworkFromFile(File(artwork))
                        newTag.setField(cover)
                    }
                } else {
                    newTag.deleteArtworkField()
                }
            }
            audioFile.commit()
            val urls = arrayOf(path)
            val mimes = arrayOf("audio/mpeg")
            MediaScannerConnection.scanFile(
                context,
                urls,
                mimes
            ) { path, uri ->
                Log.i(
                    "Audiotagger",
                    "Media scanning success"
                )
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun readTags(path: String): Map<String, String>? {
        try {
            val mp3File = File(path)
            val audioFile = AudioFileIO.read(mp3File)
            val map: MutableMap<String, String> = HashMap()
            val tag = audioFile.tag
            if (tag != null) {
                map["title"] = tag.getFirst(FieldKey.TITLE)
                map["artist"] = tag.getFirst(FieldKey.ARTIST)
                map["genre"] = tag.getFirst(FieldKey.GENRE)
                map["trackNumber"] = tag.getFirst(FieldKey.TRACK)
                map["trackTotal"] = tag.getFirst(FieldKey.TRACK_TOTAL)
                map["discNumber"] = tag.getFirst(FieldKey.DISC_NO)
                map["discTotal"] = tag.getFirst(FieldKey.DISC_TOTAL)
                map["lyrics"] = tag.getFirst(FieldKey.LYRICS)
                map["comment"] = tag.getFirst(FieldKey.COMMENT)
                map["album"] = tag.getFirst(FieldKey.ALBUM)
                map["albumArtist"] = tag.getFirst(FieldKey.ALBUM_ARTIST)
                map["year"] = tag.getFirst(FieldKey.YEAR)
            }
            return map
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun readArtwork(path: String): ByteArray? {
        try {
            val mp3File = File(path)
            val audioFile = AudioFileIO.read(mp3File)
            val tag = audioFile.tag
            if (tag != null) {
                val artwork = tag.firstArtwork
                if (artwork != null) return artwork.binaryData
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun readAudioFile(path: String): Map<String, Any>? {
        try {
            val mp3File = File(path)
            val audioFile = AudioFileIO.read(mp3File)
            val map: MutableMap<String, Any> = HashMap()
            val audioHeader = audioFile.audioHeader
            if (audioHeader != null) {
                map["length"] = audioHeader.trackLength
                map["bitRate"] = audioHeader.bitRateAsNumber
                map["channels"] = audioHeader.channels
                map["encodingType"] = audioHeader.encodingType
                map["format"] = audioHeader.format
                map["sampleRate"] = audioHeader.sampleRateAsNumber
                map["isVariableBitRate"] = audioHeader.isVariableBitRate
            }
            return map
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    internal enum class Version {
        ID3V1, ID3V2
    }

    internal object Util {
        @Throws(FieldDataInvalidException::class)
        fun setFieldIfExist(tag: Tag, field: FieldKey?, map: Map<String?, String?>, key: String?) {
            val value = map[key]
            // If field is null, it is ignored
            if (value != null) {
                // If field is set to an empty string, the field is deleted, otherwise it is set
                if (value.trim().isNotEmpty()) {
                    tag.setField(field, value)
                } else {
                    tag.deleteField(field)
                }
            }
        }
    }
}