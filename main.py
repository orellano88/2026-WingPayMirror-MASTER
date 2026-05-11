import os
import threading
import requests
import json
import math
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.widget import Widget
from kivy.clock import Clock, mainthread
from kivy.core.window import Window
from kivy.graphics import Color, RoundedRectangle, RenderContext, Rectangle, BindTexture, Fbo, ClearColor, ClearBuffers, Scale, Translate, SmoothLine
from kivy.properties import StringProperty, ListProperty, BooleanProperty, NumericProperty, ObjectProperty
from kivy.lang import Builder
from kivy.metrics import dp

# --- PROTOCOLO STARK v58.0 HOLOGRAPHIC MASTER ---

# SHADERS: DIFUMINADO GAUSSIANO (FROSTED GLASS)
vertical_blur_shader = """
#ifdef GL_ES
    precision lowp float;
#endif
varying vec4 frag_color;
varying vec2 tex_coord0;
uniform sampler2D texture0;
uniform float mean_res;
uniform float blur_size;

void main (void){
    float dt = ((blur_size / 2.0) * 1.0 / mean_res);
    vec4 sum = vec4(0.0);
    sum += texture2D(texture0, vec2(tex_coord0.x, tex_coord0.y+3.0*dt))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x, tex_coord0.y+2.0*dt))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x, tex_coord0.y))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x, tex_coord0.y-2.0*dt))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x, tex_coord0.y-3.0*dt))*0.077;
    gl_FragColor = frag_color * vec4(sum.rgba);
}
"""

horizontal_blur_shader = """
#ifdef GL_ES
    precision lowp float;
#endif
varying vec4 frag_color;
varying vec2 tex_coord0;
uniform sampler2D texture0;
uniform float mean_res;
uniform float blur_size;

void main (void){
    float dt = (blur_size / 2.0) * 1.0 / mean_res;
    vec4 sum = vec4(0.0);
    sum += texture2D(texture0, vec2(tex_coord0.x+3.0*dt, tex_coord0.y))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x+2.0*dt, tex_coord0.y))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x, tex_coord0.y))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x-2.0*dt, tex_coord0.y))*0.077;
    sum += texture2D(texture0, vec2(tex_coord0.x-3.0*dt, tex_coord0.y))*0.077;
    gl_FragColor = frag_color * vec4(sum.rgba);
}
"""

# SHADER: LIQUID GRADIENT BACKGROUND
liquid_bg_shader = """
#ifdef GL_ES
    precision highp float;
#endif
varying vec2 tex_coord0;
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = tex_coord0;
    vec3 color1 = vec3(0.05, 0.1, 0.15); // Deep Stark Blue
    vec3 color2 = vec3(0.1, 0.3, 0.4);   // Neural Cyan
    
    float wave = sin(uv.x * 3.0 + time * 0.5) * cos(uv.y * 2.0 + time * 0.3);
    vec3 color = mix(color1, color2, uv.y + wave * 0.2);
    
    gl_FragColor = vec4(color, 1.0);
}
"""

class FrostedPanel(FloatLayout):
    background = ObjectProperty(None)
    blur_size = NumericProperty(15)
    border_radius = ListProperty([20, 20, 20, 20])

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.canvas = RenderContext(use_parent_projection=True, use_parent_modelview=True)
        # Simplificación para performance en móvil
        self.h_fbo = Fbo(size=(256, 256), fs=horizontal_blur_shader)
        self.v_fbo = Fbo(size=(256, 256), fs=vertical_blur_shader)
        
        with self.canvas:
            self.bg_bind = BindTexture(index=1)
            self.rect = RoundedRectangle(size=self.size, pos=self.pos, radius=self.border_radius)
        
        Clock.schedule_interval(self.update_blur, 1/30.)

    def update_blur(self, *args):
        if not self.background: return
        self.rect.size = self.size
        self.rect.pos = self.pos
        # Captura simplificada para no matar el CPU
        self.h_fbo.size = (self.width/2, self.height/2)
        self.v_fbo.size = (self.width/2, self.height/2)
        self.bg_bind.texture = self.background.export_as_image().texture

class LiquidBackground(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.canvas = RenderContext(use_parent_projection=True, fs=liquid_bg_shader)
        with self.canvas:
            self.rect = Rectangle(size=Window.size, pos=(0,0))
        Clock.schedule_interval(self.update_shader, 1/60.)

    def update_shader(self, dt):
        self.canvas['time'] = Clock.get_boottime()
        self.canvas['resolution'] = [float(v) for v in Window.size]
        self.rect.size = Window.size

class StarkHolographicApp(App):
    status_ntfy = StringProperty("🟢")
    status_pc = StringProperty("⚪")

    def build(self):
        root = FloatLayout()
        
        # 1. Capa de Fondo Líquido (Shader)
        self.bg = LiquidBackground()
        root.add_widget(self.bg)

        # 2. Capa de Interfaz (Glassmorphism)
        main_layout = BoxLayout(orientation='vertical', padding=dp(20), spacing=dp(15))
        
        # Cabecera Master
        header = BoxLayout(size_hint_y=None, height=dp(80), spacing=dp(10))
        header.add_widget(Label(text="2026 WING MASTER\nHOLOGRAPHIC v58.0", bold=True, font_size='22sp', color=(0, 0.9, 1, 1)))
        
        leds = BoxLayout(size_hint_x=None, width=dp(100), orientation='vertical')
        self.lbl_ntfy = Label(text=f"RED: {self.status_ntfy}", font_size='12sp')
        self.lbl_pc = Label(text=f"SYNC: {self.status_pc}", font_size='12sp')
        leds.add_widget(self.lbl_ntfy); leds.add_widget(self.lbl_pc)
        header.add_widget(leds)
        
        main_layout.add_widget(header)

        # Stark Terminal (Frosted Glass)
        term_panel = FrostedPanel(size_hint_y=None, height=dp(200), background=self.bg)
        self.terminal = Label(text="[STARK_OS]: Sistema Neural v58 Online\n[SINC]: Puente Master Activo", 
                             font_size='13sp', font_name='Roboto', color=(0, 1, 0.4, 1), halign='left', valign='top')
        term_panel.add_widget(self.terminal)
        main_layout.add_widget(term_panel)

        # Espacio para Pagos
        self.scroll = ScrollView()
        self.payment_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(10))
        self.payment_list.bind(minimum_height=self.payment_list.setter('height'))
        self.scroll.add_widget(self.payment_list)
        main_layout.add_widget(self.scroll)

        # Botonera Master
        btn_bar = BoxLayout(size_hint_y=None, height=dp(70), spacing=dp(10))
        btn_bar.add_widget(Button(text="🧪 TEST", background_color=(0, 0.5, 0.8, 0.6), on_release=self.run_test))
        btn_bar.add_widget(Button(text="🚨 SOS", background_color=(0.8, 0.1, 0.1, 0.6), on_release=self.trigger_sos))
        main_layout.add_widget(btn_bar)

        root.add_widget(main_layout)
        
        # Iniciar Escucha (ntfy.sh)
        threading.Thread(target=self.ntfy_listener, daemon=True).start()
        
        return root

    def ntfy_listener(self):
        topic = "wingpay_client_A2ZQV4"
        url = f"https://ntfy.sh/{topic}/json"
        while True:
            try:
                with requests.get(url, stream=True, timeout=None) as r:
                    self.status_ntfy = "🟢"
                    for line in r.iter_lines():
                        if line:
                            data = json.loads(line)
                            if "message" in data:
                                msg_data = json.loads(data["message"])
                                if msg_data.get("sender") == "PC":
                                    self.status_pc = "🔵"
                                    self.log_terminal(f"SYNC_PC: {msg_data.get('message')}")
                                    Clock.schedule_once(lambda dt: setattr(self, 'status_pc', "⚪"), 2)
            except:
                self.status_ntfy = "🔴"
                import time; time.sleep(10)

    @mainthread
    def log_terminal(self, text):
        self.terminal.text = f"{self.terminal.text}\n[{datetime.now().strftime('%H:%M')}] {text}"

    def run_test(self, *args):
        self.log_terminal("CMD: TEST_SINC_MASTER")
        # Aquí iría el envío de intent al servicio Kotlin v57.2

    def trigger_sos(self, *args):
        self.log_terminal("ALERTA: SOS_ENVIADO_A_PC")

if __name__ == '__main__':
    StarkHolographicApp().run()
