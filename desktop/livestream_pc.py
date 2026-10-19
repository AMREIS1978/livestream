"""LiveStream Windows - free desktop screen sharing and local recording."""
import io
import os
import queue
import secrets
import threading
import time
import uuid
import webbrowser
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
from urllib.request import Request, urlopen
from urllib.error import URLError
import mss
from PIL import Image

SERVER = "https://livestremanew.a-miguel-reis.workers.dev"
MAX_BYTES = 690000

class LiveStreamPC:
    def __init__(self, root):
        self.root = root
        self.root.title("LiveStream · Windows")
        self.root.geometry("540x490")
        self.root.minsize(480, 450)
        self.root.configure(bg="#f6f8fc")
        self.running = threading.Event()
        self.recording = False
        self.thread = None
        self.selected_monitor = 1
        self.want_record = False
        self.session = ""
        self.key = ""
        self.video = None
        self.status_queue = queue.Queue()
        self.monitor = tk.IntVar(value=1)
        self.record = tk.BooleanVar(value=False)
        self._label("● LiveStream", 18, "#1877f2", True).pack(anchor="w", padx=30, pady=(25, 8))
        self._label("Partilhe o ecrã. Sem complicações.", 18, "#1c2940", True).pack(anchor="w", padx=30)
        self._label("Transmita para qualquer navegador através de um link.", 10, "#667085").pack(anchor="w", padx=30, pady=(4, 20))
        self.status = tk.StringVar(value="Pronto para começar")
        ttk.Label(root, textvariable=self.status).pack(anchor="w", padx=30, pady=(0, 12))
        self.monitor_menu = ttk.Combobox(root, state="readonly")
        with mss.mss() as cap:
            self.monitor_menu["values"] = [f"Monitor {i}" for i in range(1, len(cap.monitors))]
        if not self.monitor_menu["values"]:
            self.monitor_menu["values"] = ["Monitor 1"]
        self.monitor_menu.current(0)
        self.monitor_menu.pack(fill="x", padx=30, pady=(0, 14))
        ttk.Checkbutton(root, text="Gravar também (MP4, se FFmpeg estiver instalado)", variable=self.record).pack(anchor="w", padx=30)
        self.start_btn = ttk.Button(root, text="▶ Iniciar transmissão", command=self.start)
        self.start_btn.pack(fill="x", padx=30, pady=(18, 8))
        self.stop_btn = ttk.Button(root, text="■ Parar e guardar", command=self.stop, state="disabled")
        self.stop_btn.pack(fill="x", padx=30, pady=5)
        self.copy_btn = ttk.Button(root, text="Copiar link", command=self.copy, state="disabled")
        self.copy_btn.pack(fill="x", padx=30, pady=5)
        self.open_btn = ttk.Button(root, text="Abrir transmissão", command=self.open, state="disabled")
        self.open_btn.pack(fill="x", padx=30, pady=5)
        self._label("A transmissão usa imagens JPEG; a gravação MP4 requer FFmpeg gratuito.\nO áudio não é capturado nesta versão.", 9, "#667085").pack(anchor="w", padx=30, pady=(20, 0))
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.root.after(200, self.poll_status)

    def _label(self, text, size, color, bold=False):
        return tk.Label(self.root, text=text, bg="#f6f8fc", fg=color,
                        font=("Segoe UI", size, "bold" if bold else "normal"), justify="left")

    @property
    def link(self):
        return f"{SERVER}/watch/{self.session}"

    def start(self):
        self.session = str(uuid.uuid4())
        self.key = secrets.token_hex(48)
        self.selected_monitor = self.monitor_menu.current() + 1
        self.want_record = self.record.get()
        self.running.set()
        self.start_btn.config(state="disabled")
        self.monitor_menu.config(state="disabled")
        self.stop_btn.config(state="normal")
        self.copy_btn.config(state="normal")
        self.open_btn.config(state="normal")
        self.thread = threading.Thread(target=self.capture, daemon=True)
        self.thread.start()

    def capture(self):
        import subprocess
        recorder = None
        try:
            with mss.mss() as cap:
                display = cap.monitors[self.selected_monitor]
                if self.want_record:
                    path = os.path.join(os.path.expanduser("~"), "Videos")
                    os.makedirs(path, exist_ok=True)
                    filename = os.path.join(path, f"LiveStream_{time.strftime('%Y%m%d_%H%M%S')}.mp4")
                    try:
                        recorder = subprocess.Popen([
                            "ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
                            "-s", f"{display['width']}x{display['height']}", "-r", "5", "-i", "-",
                            "-an", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", filename
                        ], stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
                        self.status_queue.put("A transmitir e gravar")
                    except (OSError, FileNotFoundError):
                        self.status_queue.put("A transmitir · FFmpeg não encontrado, sem gravação")
                while self.running.is_set():
                    started = time.monotonic()
                    screenshot = cap.grab(display)
                    image = Image.frombytes("RGB", screenshot.size, screenshot.rgb)
                    if recorder and recorder.poll() is None:
                        try:
                            recorder.stdin.write(image.tobytes())
                        except (BrokenPipeError, OSError):
                            recorder = None
                            self.status_queue.put("Gravação interrompida · transmissão continua")
                    preview = image.copy()
                    preview.thumbnail((960, 960))
                    body = None
                    for quality in (72, 60, 45, 30):
                        out = io.BytesIO()
                        preview.save(out, "JPEG", quality=quality, optimize=False)
                        if out.tell() <= MAX_BYTES:
                            body = out.getvalue()
                            break
                    if body:
                        req = Request(f"{SERVER}/api/frame/{self.session}", data=body, method="POST",
                                      headers={"Content-Type": "image/jpeg", "X-Publish-Key": self.key})
                        try:
                            with urlopen(req, timeout=4) as response:
                                if response.status == 204:
                                    self.status_queue.put("● Em direto")
                        except (URLError, OSError) as exc:
                            self.status_queue.put("A tentar restabelecer ligação…")
                    elapsed = time.monotonic() - started
                    time.sleep(max(0, .2 - elapsed))
        except Exception as exc:
            self.status_queue.put(f"Erro: {type(exc).__name__}")
        finally:
            if recorder and recorder.poll() is None:
                try:
                    recorder.stdin.close()
                    recorder.wait(timeout=15)
                except (OSError, subprocess.TimeoutExpired):
                    recorder.kill()
            self.running.clear()
            self.status_queue.put("Transmissão terminada")

    def stop(self):
        self.running.clear()
        self.stop_btn.config(state="disabled")
        self.start_btn.config(state="normal")
        self.monitor_menu.config(state="readonly")

    def copy(self):
        if self.session:
            self.root.clipboard_clear()
            self.root.clipboard_append(self.link)
            self.status.set("Link copiado")

    def open(self):
        if self.session:
            webbrowser.open(self.link)

    def poll_status(self):
        try:
            while True:
                self.status.set(self.status_queue.get_nowait())
        except queue.Empty:
            pass
        self.root.after(200, self.poll_status)

    def close(self):
        self.running.clear()
        self.root.destroy()

if __name__ == "__main__":
    root = tk.Tk()
    LiveStreamPC(root)
    root.mainloop()
