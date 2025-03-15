// Navbar toggle
document.addEventListener('DOMContentLoaded', () => {
    const hamburgers = document.querySelectorAll('.hamburger');
    const navMenus = document.querySelectorAll('.nav-menu');

    hamburgers.forEach((hamburger, index) => {
        const navMenu = navMenus[index];

        if (hamburger && navMenu) {
            hamburger.addEventListener('click', (e) => {
                e.stopPropagation(); // Prevent event bubbling
                hamburger.classList.toggle('active');
                navMenu.classList.toggle('active');
            });

            // Close menu when clicking outside
            document.addEventListener('click', (e) => {
                if (!e.target.closest('.nav-container') && !e.target.closest('.nav-menu')) {
                    hamburger.classList.remove('active');
                    navMenu.classList.remove('active');
                }
            });
        }
    });
}); 