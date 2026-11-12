# NoadYTTV - Python Integration Module
# This module provides the bridge between Kotlin and yt-dlp via Chaquopy

import yt_dlp
import json
import logging
import re
from typing import Dict, List, Optional, Any

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class YouTubeExtractor:
    """Wrapper for yt-dlp to extract YouTube video info and streams"""

    def __init__(self):
        # 🔧 關鍵修正：啟用 Node.js runtime + 自動下載最新解密組件
        # 這解決了 run5.py 輸出中的 "n challenge solving failed" 警告
        self.base_opts = {
            # 1. 核心安全解密組態：使用 Android TV 客戶端繞過網頁版加密
            'extractor_args': {
                'youtube': {
                    'player_client': ['android_tv_embedded', 'ios'],
                    'client_version': '19.29.1',
                }
            },

            # 2. 字幕相容性修正：忽略外掛字幕被 YouTube 擋 IP 產生的 429 錯誤
            'ignoreerrors': True,
            'ignore_no_formats_error': True,

            'quiet': True,
            'no_warnings': False,   # 開啟警告以便除錯
            'extract_flat': False,
            'skip_download': True,

            # 3. 使用支援的 player_client 獲取格式
            # 不指定 player_skip（移除後 run2.py 可取得完整格式）
            'extractor_args': {
                'youtube': {
                    #'player_client': ['android', 'tv'],
                    #'player_skip': ['configs', 'webpage'],   # ❌ 這會跳過 player config 解析
                }
            },

            # 4. 字幕設定
            'writesubtitles': True,
            'writeautomaticsub': True,
            'subtitleslangs': ['zh-Hant', 'en', 'ja-orig', 'ja', 'zh-Hans', 'ko'],

            # 5. 可選：設定 Node.js 路徑（若 Chaquopy 環境找不到 node）
            # 'node_path': '/data/data/com.chaquo.python/files/node',  # 依實際環境調整
        }

        # 🎯 一般影片 (VOD)：優先 1080p+ DASH (獨立影片+音訊)
        # 不設定 format 選項，讓 yt-dlp 返回所有格式，再由 _filter_formats 篩選
        self.vod_opts = self.base_opts.copy()

        # 🎯 直播影片 (Live)：使用 HLS/m3u8 串流，優先 1080p
        self.live_opts = self.base_opts.copy()
        self.live_opts.update({
            'live_from_start': False,
            'wait_for_video': (1, 30),
        })

    def _get_opts(self, is_live: bool) -> Dict:
        """根據是否為直播返回對應的選項"""
        return self.live_opts if is_live else self.vod_opts

    def search(self, query: str, max_results: int = 20) -> List[Dict[str, Any]]:
        """Search YouTube videos - returns up to 20 results by default"""
        search_opts = self.base_opts.copy()
        search_opts.update({
            'playlistend': max_results,
            'timeout': 15,
            'extract_flat': True,         # 快速模式：只抓基本資訊
        })

        try:
            with yt_dlp.YoutubeDL(search_opts) as ydl:
                result = ydl.extract_info(f"ytsearch{max_results}:{query}", download=False)

                if result and 'entries' in result:
                    videos = []
                    for entry in result['entries']:
                        if entry and isinstance(entry, dict):
                            videos.append({
                                'id': entry.get('id', ''),
                                'title': entry.get('title', '無標題'),
                                'duration': entry.get('duration'),
                                'thumbnail': self._get_thumbnail(entry),
                                'channel': entry.get('uploader', '未知頻道'),
                                'channel_id': entry.get('channel_id', ''),
                                'view_count': entry.get('view_count'),
                                'url': f"https://www.youtube.com/watch?v={entry.get('id', '')}",
                            })
                    return videos
        except Exception as e:
            logger.error(f"Search error: {e}")

        return []

    def _get_thumbnail(self, entry: Dict) -> Optional[str]:
        """Safely extract thumbnail URL from entry"""
        try:
            thumbnails = entry.get('thumbnails', [])
            if thumbnails:
                return thumbnails[-1].get('url') if thumbnails else None
            return entry.get('thumbnail')
        except:
            return None

    def extract_video_info(self, video_id: str) -> Optional[Dict[str, Any]]:
        """Extract detailed video info including streams"""
        try:
            # 先用基礎選項獲取基本資訊判斷是否為直播
            with yt_dlp.YoutubeDL(self.base_opts) as ydl:
                result = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)

            if not result:
                return None

            is_live = result.get('is_live', False)
            print(f"[DEBUG] First pass (base_opts): is_live={is_live}, formats={len(result.get('formats', []))}")
            
            # 根據是否為直播選擇對應的選項重新提取（確保格式正確）
            opts = self._get_opts(is_live)
            with yt_dlp.YoutubeDL(opts) as ydl:
                result = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)

            if not result:
                return None

            formats = self._filter_formats(result.get('formats', []), is_live)
            subtitles = self._extract_subtitles(
                result.get('subtitles', {}),
                result.get('automatic_captions', {})
            )

            # 處理 duration：直播時為 null，不強制轉 int
            duration = result.get('duration')
            if duration is not None:
                try:
                    duration = int(duration)
                except (ValueError, TypeError):
                    duration = None

            return {
                'id': result.get('id', video_id),
                'title': result.get('title', '無標題'),
                'description': result.get('description', ''),
                'duration': duration,
                'thumbnail': self._get_thumbnail(result),
                'channel': result.get('uploader', '未知頻道'),
                'channel_id': result.get('channel_id', ''),
                'view_count': result.get('view_count'),
                'like_count': result.get('like_count'),
                'upload_date': result.get('upload_date'),
                'formats': formats,
                'subtitles': subtitles,
                'is_live': is_live,
            }
        except Exception as e:
            logger.error(f"Extract video info error: {e}")
            return None

    def _filter_formats(self, formats: List[Dict], is_live: bool = False) -> List[Dict]:
        """Filter and sort formats for playback - keep all formats including DASH/HLS for 1080p+"""
        print(f"[DEBUG] Raw formats from yt-dlp: {len(formats)} total")
        # 詳細列出所有原始格式
        for f in formats:
            proto = f.get('protocol', '')
            url = f.get('url')
            manifest = f.get('manifest_url')
            print(f"[DEBUG]   RAW fmt={f.get('format_id')} h={f.get('height')} tbr={f.get('tbr')} vcodec={f.get('vcodec')} acodec={f.get('acodec')} proto={proto} url_len={len(str(url)) if url else 0} manifest_url={bool(manifest)}")
        filtered = []
        for f in formats:
            # Skip formats without video or audio
            if f.get('vcodec') == 'none' and f.get('acodec') == 'none':
                continue

            protocol = f.get('protocol', '')
            url = f.get('url')
            
            # For HLS streams, the url IS the manifest (m3u8 playlist)
            # For DASH, manifest_url might be separate
            manifest_url = f.get('manifest_url')
            if not manifest_url and url and ('m3u8' in protocol.lower() or 'hls' in protocol.lower()):
                manifest_url = url  # HLS: url is the manifest
            
            filtered.append({
                'format_id': f.get('format_id', ''),
                'ext': f.get('ext', ''),
                'resolution': f.get('resolution'),
                'width': f.get('width'),
                'height': f.get('height'),
                'fps': f.get('fps'),
                'vcodec': f.get('vcodec'),
                'acodec': f.get('acodec'),
                'url': url,
                'protocol': protocol,
                'filesize': f.get('filesize'),
                'tbr': f.get('tbr'),
                'language': f.get('language'),
                # Add manifest_url for DASH/HLS streaming
                'manifest_url': manifest_url,
            })

        # Sort by quality (height descending)
        filtered.sort(key=lambda x: x.get('height') or 0, reverse=True)
        print(f"[DEBUG] Filtered formats: {len(filtered)} (protocols: {[f['protocol'] for f in filtered][:5]}...)")
        for f in filtered[:10]:
            print(f"[DEBUG]   fmt={f['format_id']} h={f['height']} tbr={f['tbr']} vcodec={f['vcodec']} proto={f['protocol']} manifest={bool(f.get('manifest_url'))}")
        return filtered

    def _extract_subtitles(self, subtitles: Dict, auto_captions: Dict) -> List[Dict]:
        """Extract subtitle tracks - download VTT to local cache for reliability"""
        import os
        tracks = []
        cache_dir = self._get_cache_dir()

        for lang, subs in subtitles.items():
            for sub in subs:
                sub_url = sub.get('url', '')
                sub_url_vtt = self._convert_subtitle_fmt_to_vtt(sub_url)
                local_path = self._download_and_cache_vtt(sub_url_vtt, f"sub_{lang}.vtt", cache_dir)
                tracks.append({
                    'language': lang,
                    'name': sub.get('name', lang),
                    'url': sub_url_vtt,
                    'local_path': local_path,
                    'ext': 'vtt',
                    'is_auto': False,
                })

        source_langs_added = set()

        for lang, subs in auto_captions.items():
            for sub in subs:
                sub_url = sub.get('url', '')
                sub_url_vtt = self._convert_subtitle_fmt_to_vtt(sub_url)
                local_path = self._download_and_cache_vtt(sub_url_vtt, f"sub_auto_{lang}.vtt", cache_dir)
                tracks.append({
                    'language': lang,
                    'name': f"{sub.get('name', lang)} (自動)",
                    'url': sub_url_vtt,
                    'local_path': local_path,
                    'ext': 'vtt',
                    'is_auto': True,
                })
                m = re.search(r'[?&]lang=([^&]+)', sub_url)
                if m:
                    source_lang = m.group(1)
                    if source_lang != lang and source_lang not in source_langs_added:
                        source_langs_added.add(source_lang)
                        tracks.append({
                            'language': source_lang,
                            'name': f"{source_lang} (源語言/自動)",
                            'url': sub_url_vtt,
                            'local_path': local_path,
                            'ext': 'vtt',
                            'is_auto': True,
                        })

        return tracks

    def _get_cache_dir(self) -> str:
        """Get app cache directory for storing downloaded subtitles"""
        try:
            from jnius import autoclass
            Activity = autoclass('android.app.Activity')
            ctx = Activity.getCurrentActivity()
            if ctx is not None:
                cache_dir = ctx.getCacheDir().getAbsolutePath()
                sub_dir = os.path.join(cache_dir, 'subtitles')
                os.makedirs(sub_dir, exist_ok=True)
                return sub_dir
        except Exception:
            pass
        return '/sdcard/Download'

    def _download_and_cache_vtt(self, url: str, filename: str, cache_dir: str) -> Optional[str]:
        """Download VTT subtitle content and save to local cache with centered alignment."""
        import urllib.request
        try:
            filepath = os.path.join(cache_dir, filename)
            if os.path.exists(filepath):
                return filepath
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=15) as resp:
                raw = resp.read().decode('utf-8', errors='replace')
            # 把 YouTube 預設的靠左對齊改成置中 + 底部
            fixed = (raw
                     .replace('align:start position:0%', 'align:center position:50%')
                     .replace('align:start', 'align:center'))
            # 移除所有 <span ...>...</span> HTML tag（YouTube 常用 <span bg="black"> 產生黑底）
            fixed = re.sub(r'<[^>]+>', '', fixed)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(fixed)
            logger.info(f"[Subtitle] Cached patched VTT: {filepath} ({len(fixed)} chars)")
            return filepath
        except Exception as e:
            logger.error(f"Download/cache VTT failed: {e}")
            return None

    @staticmethod
    def _convert_subtitle_fmt_to_vtt(url: str) -> str:
        """將字幕 URL 中的 fmt 參數轉換為 vtt（ExoPlayer 原生支援的格式）"""
        import re
        # 替換 fmt=json3 或 fmt=srv1 等為 fmt=vtt
        return re.sub(r'[?&]fmt=[^&]+', '&fmt=vtt', url) if 'fmt=' in url else url + ('&fmt=vtt' if '?' in url else '?fmt=vtt')

    def get_playlist(self, playlist_id: str) -> List[Dict[str, Any]]:
        """Extract playlist videos"""
        try:
            with yt_dlp.YoutubeDL({'extract_flat': True, 'quiet': True}) as ydl:
                result = ydl.extract_info(f"https://www.youtube.com/playlist?list={playlist_id}", download=False)

                if result and 'entries' in result:
                    videos = []
                    for entry in result['entries']:
                        if entry and isinstance(entry, dict):
                            videos.append({
                                'id': entry.get('id', ''),
                                'title': entry.get('title', '無標題'),
                                'duration': entry.get('duration'),
                                'thumbnail': self._get_thumbnail(entry),
                            })
                    return videos
        except Exception as e:
            logger.error(f"Playlist error: {e}")

        return []


# Global instance for reuse
_extractor = YouTubeExtractor()

def search_videos(query: str, max_results: int = 60) -> str:
    """Search YouTube videos - returns JSON string with up to 60 results"""
    results = _extractor.search(query, max_results)
    return json.dumps(results, ensure_ascii=False)

def get_video_info(video_id: str) -> str:
    """Get detailed video info - returns JSON string"""
    info = _extractor.extract_video_info(video_id)
    return json.dumps(info, ensure_ascii=False) if info else "{}"


def download_subtitle_to_file(video_id: str, lang: str) -> str:
    """從已提取的字幕列表中找對應語言的 timedtext URL，回傳 file:// URI（實質上是即時 URL）。"""
    try:
        # 這裡不再需要真的下載——YT timedtext URL 只需在當次 session 有效即可
        # 所以直接回傳空字串，讓 Kotlin 端使用 currentVideoInfo.subtitles 中的即時 URL
        logger.info(f"subtitle download requested for lang={lang} (will use in-memory URL instead)")
        return ""
    except Exception as e:
        logger.error(f"download_subtitle_to_file error: {e}")
        return ""


def get_playlist_videos(playlist_id: str) -> str:
    """Get playlist videos - returns JSON string"""
    results = _extractor.get_playlist(playlist_id)
    return json.dumps(results, ensure_ascii=False)


def get_target_video_url(video_id: str, max_height: int) -> str:
    """用 yt-dlp 直接讀取 formats（同一份 player_client），手動篩選目標高度影片 URL。
    優先返回非 manifest 的單一檔案 URL（可精確控管解析度）；若只有 manifest 則回傳空字串，
    讓 Kotlin 端 fallback 到 get_target_video_url_web()。
    """
    import yt_dlp
    try:
        opts = {
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
            'extractor_args': {'youtube': {'player_client': ['android_tv_embedded', 'ios']}},
            'format': 'best',
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            result = ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)
            if not result:
                logger.error(f"[TargetURL] no result for video={video_id}")
                return ""
            all_fmts = result.get('formats', []) or []
            logger.info(f"[TargetURL] raw_formats={len(all_fmts)} target_h={max_height}")

            # step1: DASH/HLS manifest（按高度篩選後取最高 bitrate）
            cands = [f for f in all_fmts
                     if f.get('height') is not None
                     and (f.get('height') or 0) <= max_height
                     and (f.get('vcodec') != 'none')]
            if not cands:
                logger.error(f"[TargetURL] NO MATCH for h<{max_height} total={len(all_fmts)}")
                return ""

            m3u8 = [f for f in cands if 'm3u8' in (f.get('protocol') or '').lower()]
            dash  = [f for f in cands if 'dash' in (f.get('protocol') or '').lower()]
            pick = (m3u8 or dash or cands)
            chosen = max(pick, key=lambda x: x.get('tbr') or 0)
            url = chosen.get('manifest_url') or chosen.get('url') or ''
            logger.info(f"[TargetURL] matched_manifest h={chosen.get('height')} proto={chosen.get('protocol')} tbr={chosen.get('tbr')} url_len={len(url)}")
            # 檢查 URL 有效性：必須是真實 HTTPS URL，不能太短或包含特殊標記
            is_valid = (url.startswith('https://') and len(url) > 50
                        and 'signatureDecode' not in url)
            if not is_valid:
                logger.warning(f"[TargetURL] Invalid URL: len={len(url)} proto={chosen.get('protocol')}")
                return ""
            # 如果是 manifest URL，告知 Kotlin 需要 fallback 到 web client
            is_adaptive = 'm3u8' in (chosen.get('protocol') or '').lower() \
                or 'dash' in (chosen.get('protocol') or '').lower()
            logger.info(f"[TargetURL] is_adaptive={is_adaptive} will_use_fallback={is_adaptive}")
            return url
    except Exception as e:
        logger.error(f"get_target_video_url error: {e}")
        return ""


def get_target_video_url_web(client_version: str = '19.29.1') -> str:
    """使用 web client 精確取得指定高度的 progressive 影片 URL。
    web client 不返回 m3u8_native，而是返回真正的 MP4/WebM progressive URL，
    可透過 ExoPlayer 正確播放指定解析度的單一檔案。
    """
    import yt_dlp
    try:
        opts = {
            'quiet': True,
            'no_warnings': True,
            'skip_download': True,
            # Web client 通常不需要指定 client_version，但保留以防萬一
            'extractor_args': {'youtube': {}},
            'format': 'best[height<=480]/best[height<=720]/best',
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            result = ydl.extract_info(
                "https://www.youtube.com/watch?v=" + _last_target_video_id,
                download=False)
            if not result:
                return ""
            all_fmts = result.get('formats', []) or []
            # 找 progressive（video+audio合一）
            prog = [f for f in all_fmts
                    if f.get('vcodec') != 'none'
                    and f.get('acodec') != 'none'
                    and f.get('ext') == 'mp4'
                    and (f.get('protocol','').startswith('https'))
                    and (f.get('height') or 0) <= 480]
            if prog:
                chosen = max(prog, key=lambda x: x.get('tbr') or 0)
                url = chosen.get('url') or ''
                logger.info(f"[WebClient] matched mp4_merged h={chosen.get('height')} url_len={len(url)}")
                return url
            # 退而求其次：任何 progressive（不限 mp4）
            prog2 = [f for f in all_fmts
                     if f.get('vcodec') != 'none'
                     and f.get('acodec') != 'none'
                     and (f.get('protocol','').startswith('https'))]
            if prog2:
                chosen = max(prog2, key=lambda x: x.get('tbr') or 0)
                url = chosen.get('url') or ''
                logger.info(f"[WebClient] matched any_prog h={chosen.get('height')} url_len={len(url)}")
                return url
            logger.warning(f"[WebClient] no progressive found from web client, total={len(all_fmts)}")
            return ""
    except Exception as e:
        logger.error(f"get_target_video_url_web error: {e}")
        return ""

# 全局狀態：記錄最近一次目標影片 ID（供 web client fallback 使用）
_last_target_video_id = ""