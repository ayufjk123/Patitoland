// 增强版导航栏交互
document.addEventListener('DOMContentLoaded', () => {
    // 菜单切换逻辑
    const setupMenu = (hamburger, navMenu) => {
        const toggleMenu = () => {
            hamburger.classList.toggle('active');
            navMenu.classList.toggle('active');
        };

        // 点击汉堡菜单
        hamburger.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleMenu();
        });

        // 触摸事件支持
        hamburger.addEventListener('touchstart', (e) => {
            e.stopPropagation();
            toggleMenu();
        });

        // 点击外部区域关闭菜单
        document.addEventListener('click', (e) => {
            if (!navMenu.contains(e.target) && !hamburger.contains(e.target)) {
                hamburger.classList.remove('active');
                navMenu.classList.remove('active');
            }
        });

        // 菜单项点击后自动关闭菜单
        navMenu.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', () => {
                hamburger.classList.remove('active');
                navMenu.classList.remove('active');
            });
        });
    };

    // 为每个导航实例初始化
    document.querySelectorAll('.nav-container').forEach(container => {
        const hamburger = container.querySelector('.hamburger');
        const navMenu = container.querySelector('.nav-menu');
        if (hamburger && navMenu) {
            setupMenu(hamburger, navMenu);
        }
    });
});