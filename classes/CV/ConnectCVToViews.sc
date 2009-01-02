+EZSlider {
	connect { arg ctl; var link;
		this.value_(ctl.value);
		this.action_({ctl.value_(this.value); });
		link = 
			SimpleController(ctl)
			.put(\synch, 
			 { arg changer, what;
			 	defer({ numberView.value = ctl.value });
			});
		this.sliderView.onClose = {link.remove};
		this.numberView.onClose = { link.remove};
	}
}
