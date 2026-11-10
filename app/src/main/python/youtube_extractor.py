# NoadYTTV - Python Integration Module
# This module provides the bridge between Kotlin and yt-dlp via Chaquopy

import yt_dlp
import json
import logging
from typing import Dict, List, Optional, Any

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class YouTubeExtractor:
    """Wrapper for yt-dlp to extract YouTube video info and streams"""

    def __init__(self):
        # 🔧 關鍵修正：啟用 Node.js runtime + 自動下載最新解密組件
        # 這解決了 run5.py 輸出中的 "n challenge solving failed" 警告
        self.base_opts = {
            # 1. 核心安全解密組態：啟用 Node.js runtime + 自動下載最新 YouTube 解密組件
            'js_runtimes': {'node': {}},           # dict 格式，相容當前 yt-dlp 版本
            'remote_components': 'ejs:github',      # 從 GitHub 自動下載 EJS 解密腳本

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

    def search(self, query: str, max_results: int = 60) -> List[Dict[str, Any]]:
        """Search YouTube videos - returns up to 60 results by default"""
        search_opts = self.base_opts.copy()
        search_opts.update({
            'extract_flat': True,
            'playlistend': max_results,
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
        """Extract subtitle tracks"""
        tracks = []

        for lang, subs in subtitles.items():
            for sub in subs:
                tracks.append({
                    'language': lang,
                    'name': sub.get('name', lang),
                    'url': sub.get('url'),
                    'ext': sub.get('ext', 'vtt'),
                    'is_auto': False,
                })

        for lang, subs in auto_captions.items():
            for sub in subs:
                tracks.append({
                    'language': lang,
                    'name': f"{sub.get('name', lang)} (自動)",
                    'url': sub.get('url'),
                    'ext': sub.get('ext', 'vtt'),
                    'is_auto': True,
                })

        return tracks

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

def get_playlist_videos(playlist_id: str) -> str:
    """Get playlist videos - returns JSON string"""
    results = _extractor.get_playlist(playlist_id)
    return json.dumps(results, ensure_ascii=False)